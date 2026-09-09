package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Non-secret last-success cache, kept as one compressed file in private storage.
 *
 * Hub JSON compresses roughly ten to one, and a file is read only when the app
 * starts, so a large history no longer has to stay resident the way a
 * SharedPreferences string would. Writes go through [AtomicFile], so a crash
 * mid-write leaves the previous snapshot intact.
 */
internal class SnapshotCache(context: Context) {
    private val file = AtomicFile(File(context.filesDir, fileName))
    private val legacyPreferences = context.getSharedPreferences(legacyPreferencesName, Context.MODE_PRIVATE)

    fun read(): WireHubSnapshot? = synchronized(ioLock) {
        runCatching { readFile() }.getOrNull() ?: migrateLegacy()
    }

    fun save(snapshot: WireHubSnapshot): Unit = synchronized(ioLock) {
        val bounded = if (snapshot.approximateBytes() <= maxUncompressedBytes) snapshot else snapshot.copy(devices = null, history = null)
        if (bounded.approximateBytes() > maxUncompressedBytes) return
        try {
            val output = file.startWrite()
            try {
                val gzip = GZIPOutputStream(output)
                DataOutputStream(gzip).also { data ->
                    data.writeInt(formatVersion)
                    data.writeLong(bounded.capturedAt)
                    data.writePart(bounded.health)
                    data.writePart(bounded.stats)
                    data.writePart(bounded.devices)
                    data.writePart(bounded.history)
                    data.writePart(bounded.subscriptions)
                    data.flush()
                }
                gzip.finish()
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        } catch (_: Exception) {
            // A later Hub update can retry; keep the previous atomic snapshot.
        }
    }

    fun clear() = synchronized(ioLock) {
        file.delete()
        legacyPreferences.edit { clear() }
    }

    private fun readFile(): WireHubSnapshot? {
        if (!file.baseFile.exists()) return null
        return DataInputStream(GZIPInputStream(BufferedInputStream(file.openRead()))).use { data ->
            if (data.readInt() != formatVersion) return null
            val capturedAt = data.readLong()
            WireHubSnapshot(
                health = data.readPart() ?: return null,
                stats = data.readPart() ?: return null,
                devices = data.readPart(),
                history = data.readPart(),
                subscriptions = data.readPart(),
                capturedAt = capturedAt,
            )
        }
    }

    /** Reads a snapshot written by the first release's SharedPreferences store, then retires it. */
    private fun migrateLegacy(): WireHubSnapshot? {
        val encoded = legacyPreferences.getString(legacySnapshotKey, null) ?: return null
        val snapshot = runCatching {
            val parts = encoded.split('.', limit = 6)
            if (parts.size != 6) return@runCatching null
            WireHubSnapshot(
                health = decodeLegacy(parts[0]),
                stats = decodeLegacy(parts[1]),
                devices = decodeLegacy(parts[2]).ifBlank { null },
                history = decodeLegacy(parts[3]).ifBlank { null },
                subscriptions = decodeLegacy(parts[4]).ifBlank { null },
                capturedAt = parts[5].toLong(),
            )
        }.getOrNull()
        legacyPreferences.edit { clear() }
        snapshot?.let(::save)
        return snapshot
    }

    private fun decodeLegacy(value: String): String =
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE).toString(StandardCharsets.UTF_8)

    private fun DataOutputStream.writePart(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readPart(): String? {
        val length = readInt()
        if (length < 0) return null
        if (length > maxUncompressedBytes) throw IllegalStateException("Cached snapshot part is too large.")
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun WireHubSnapshot.approximateBytes(): Int =
        health.length + stats.length + (devices?.length ?: 0) + (history?.length ?: 0) + (subscriptions?.length ?: 0)

    private companion object {
        // Repository and widget use separate instances of AtomicFile in one process.
        val ioLock = Any()
        const val fileName = "last-snapshot.bin"
        const val formatVersion = 2
        // Generous: a year of per-model daily history is well under this, and it gzips to a tenth.
        const val maxUncompressedBytes = 16 * 1024 * 1024
        const val legacyPreferencesName = "snapshot_cache"
        const val legacySnapshotKey = "last_success_v1"
    }
}
