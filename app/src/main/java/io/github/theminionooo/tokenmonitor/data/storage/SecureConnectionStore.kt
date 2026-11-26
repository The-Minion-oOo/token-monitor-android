package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal const val LEGACY_HUB_PROFILE = "legacy"
private val profileIdPattern = Regex("[a-f0-9]{32}")
internal fun validHubProfileId(id: String): Boolean = id == LEGACY_HUB_PROFILE || profileIdPattern.matches(id)

internal data class SavedHubProfile(val id: String, val name: String, val connection: HubConnection)
internal data class HubProfileSummary(val id: String, val name: String, val address: String)
internal data class SavedHubProfiles(val activeId: String? = null, val entries: List<SavedHubProfile> = emptyList()) {
    val active get() = entries.firstOrNull { it.id == activeId }
    fun summaries(): List<HubProfileSummary> = entries.map { HubProfileSummary(it.id, it.name, it.connection.baseUrl) }
}

/**
 * Encrypts the pairing record with a non-exportable Android Keystore AES key.
 * The ciphertext in SharedPreferences is useless outside this app and device.
 */
internal class SecureConnectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_connection", Context.MODE_PRIVATE)

    fun read(): HubConnection? = profiles().active?.connection

    /** Selects local cache and job preferences without opening a credential. */
    fun localActiveId(): String? = synchronized(storeLock) {
        val marker = preferences.getString(activeIdKey, null)
        if (marker != null) return@synchronized marker.takeIf(::validHubProfileId)
        LEGACY_HUB_PROFILE.takeIf { preferences.contains(ivKey) && preferences.contains(ciphertextKey) }
    }

    fun profiles(): SavedHubProfiles = synchronized(storeLock) {
        val values = preferences.all
        val iv = values[ivKey] as? String ?: return@synchronized SavedHubProfiles()
        val ciphertext = values[ciphertextKey] as? String ?: return@synchronized SavedHubProfiles()
        runCatching { decodeProfiles(decrypt(iv, ciphertext)) }.getOrDefault(SavedHubProfiles())
    }

    fun save(
        connection: HubConnection,
        resetAliases: Boolean = true,
        addedName: String? = null,
        newCacheIdentity: Boolean = false,
    ): SavedHubProfiles = synchronized(storeLock) {
        val current = profiles()
        val previous = current.active
        val id = when {
            addedName != null || newCacheIdentity || (previous == null && current.entries.isNotEmpty()) ->
                UUID.randomUUID().toString().replace("-", "")
            else -> previous?.id ?: LEGACY_HUB_PROFILE
        }
        val saved = SavedHubProfile(id, cleanName(addedName ?: previous?.name.orEmpty()), connection)
        val replaced = previous?.takeIf { addedName == null }
        val entries = if (replaced == null) current.entries + saved else current.entries.map { if (it.id == replaced.id) saved else it }
        write(SavedHubProfiles(id, entries), replaced?.id?.takeIf { resetAliases || it != id })
    }

    fun activate(id: String): SavedHubProfiles? = synchronized(storeLock) {
        val current = profiles()
        if (current.entries.none { it.id == id }) return@synchronized null
        if (id == current.activeId) current else write(current.copy(activeId = id))
    }

    fun rename(id: String, name: String): SavedHubProfiles = synchronized(storeLock) {
        val current = profiles()
        write(current.copy(entries = current.entries.map { if (it.id == id) it.copy(name = cleanName(name)) else it }))
    }

    fun remove(id: String): SavedHubProfiles = synchronized(storeLock) {
        val current = profiles()
        write(SavedHubProfiles(current.activeId?.takeUnless { it == id }, current.entries.filterNot { it.id == id }), id)
    }

    fun clear() = synchronized(storeLock) { preferences.edit(commit = true) { clear() } }

    /** Display data is namespaced alongside the profile that supplied it. */
    fun readModelAliases(id: String? = profiles().activeId): String? = id?.let { preferences.getString(aliasKey(it), null) }

    fun saveModelAliases(raw: String?) = synchronized(storeLock) {
        val id = profiles().activeId ?: return@synchronized
        preferences.edit { putString(aliasKey(id), raw) }
    }

    private fun cleanName(name: String): String = name.filterNot { it.isISOControl() }.trim()

    private fun aliasKey(id: String): String = if (id == LEGACY_HUB_PROFILE) "model_aliases" else "model_aliases_$id"

    private fun write(profiles: SavedHubProfiles, clearAliasesFor: String? = null): SavedHubProfiles {
        val payload = buildJsonObject {
            put("activeId", profiles.activeId)
            put("entries", buildJsonArray {
                for (profile in profiles.entries) add(buildJsonObject {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("pairing", encode(profile.connection))
                })
            })
        }
        val encrypted = encrypt(profilePrefix + payload.toString())
        val before = preferences.all
        val alias = clearAliasesFor?.let(::aliasKey)
        val editor = preferences.edit()
            .putString(ivKey, encrypted.iv)
            .putString(ciphertextKey, encrypted.ciphertext)
            .putString(activeIdKey, profiles.activeId.orEmpty())
        alias?.let(editor::remove)
        if (!editor.commit()) {
            // SharedPreferences applies a failed disk commit in memory. Restore the previous view.
            preferences.edit(commit = true) {
                putString(ivKey, before[ivKey] as? String)
                putString(ciphertextKey, before[ciphertextKey] as? String)
                putString(activeIdKey, before[activeIdKey] as? String)
                alias?.let { putString(it, before[it] as? String) }
            }
            throw IOException("Hub profile could not be saved.")
        }
        return profiles
    }

    private fun decodeProfiles(value: String): SavedHubProfiles {
        if (!value.startsWith(profilePrefix)) {
            val legacy = decode(value) ?: return SavedHubProfiles()
            return SavedHubProfiles(LEGACY_HUB_PROFILE, listOf(SavedHubProfile(LEGACY_HUB_PROFILE, "", legacy)))
        }
        val payload = Json.parseToJsonElement(value.removePrefix(profilePrefix)) as? JsonObject ?: return SavedHubProfiles()
        val entries = (payload["entries"] as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = entry.string("id")?.takeIf(::validHubProfileId) ?: return@mapNotNull null
            val pairing = entry.string("pairing")?.let(::decode) ?: return@mapNotNull null
            SavedHubProfile(id, cleanName(entry.string("name").orEmpty()), pairing)
        }.distinctBy { it.id }
        return SavedHubProfiles(payload.string("activeId")?.takeIf { id -> entries.any { it.id == id } }, entries)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return EncryptedValue(
            iv = Base64.encodeToString(cipher.iv, base64Flags),
            ciphertext = Base64.encodeToString(
                cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
                base64Flags,
            ),
        )
    }

    private fun decrypt(encodedIv: String, encodedCiphertext: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(encodedIv, base64Flags)),
        )
        return cipher.doFinal(Base64.decode(encodedCiphertext, base64Flags)).toString(StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(keyStoreName).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    // Layout: url.secret.allowLocal.fallbackUrl.allowPublicHttps. Empty fallback
    // keeps the consent field in a fixed position. Old 3/4-field records stay off.
    internal fun encode(connection: HubConnection): String = listOf(
        encodePart(connection.baseUrl),
        encodePart(connection.secret),
        connection.allowLocalNetwork.toString(),
        encodePart(connection.fallbackUrl.orEmpty()),
        connection.allowPublicHttps.toString(),
    ).joinToString(".")

    internal fun decode(value: String): HubConnection? {
        val parts = value.split('.', limit = 6)
        if (parts.size !in 3..5) return null
        val url = decodePart(parts[0]) ?: return null
        val secret = decodePart(parts[1]) ?: return null
        val allowLocalNetwork = parts[2].toBooleanStrictOrNull() ?: return null
        val fallbackUrl = parts.getOrNull(3)?.let { decodePart(it) ?: return null }?.takeIf { it.isNotBlank() }
        val allowPublicHttps = parts.getOrNull(4)?.let { it.toBooleanStrictOrNull() ?: return null } ?: false
        return HubConnection(url, secret, allowLocalNetwork, fallbackUrl, allowPublicHttps)
    }

    private fun encodePart(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
        base64Flags,
    )

    private fun decodePart(value: String): String? = runCatching {
        Base64.decode(value, base64Flags).toString(StandardCharsets.UTF_8)
    }.getOrNull()

    private data class EncryptedValue(val iv: String, val ciphertext: String)

    private companion object {
        val storeLock = Any()
        const val profilePrefix = "profiles-v1:"
        const val activeIdKey = "active_profile_id"
        const val keyStoreName = "AndroidKeyStore"
        const val keyAlias = "token_monitor_hub_connection_v1"
        const val transformation = "AES/GCM/NoPadding"
        const val ivKey = "iv"
        const val ciphertextKey = "ciphertext"
        const val base64Flags = Base64.NO_WRAP or Base64.URL_SAFE
    }
}
