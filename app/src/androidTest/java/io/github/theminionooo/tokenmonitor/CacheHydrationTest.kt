package io.github.theminionooo.tokenmonitor

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import android.util.Base64
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.LEGACY_HUB_PROFILE
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshInterval
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshSettings
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.widget.WidgetRuntime
import io.github.theminionooo.tokenmonitor.widget.WidgetSession
import io.github.theminionooo.tokenmonitor.widget.loadWidgetSnapshot
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CacheHydrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun isolatedContext(): Context {
        val base = instrumentation.targetContext
        check(base.packageName.endsWith(".preview"))
        val prefix = "cache_hydration_${UUID.randomUUID()}"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("${prefix}_$name", mode)
        }
    }

    private val wire = WireHubSnapshot(
        """{"ok":true,"role":"hub"}""", """{"updatedAt":"old","periods":{"today":{"totalTokens":42}}}""",
        null, null, null, 1234,
    )

    @Test fun completeEnvelopeIsNormalizedOnlyWhenSavedAndStillAcceptsFreshness() {
        val context = isolatedContext()
        val cache = SnapshotCache(context)
        val envelope = wire.copy(stats = """{"reason":"synthetic","stats":${wire.stats}}""")
        try {
            cache.save(envelope)
            val saved = requireNotNull(cache.read())
            assertEquals(HubProtocolParser.decodeStats(wire.stats), HubProtocolParser.decodeStats(saved.stats))
            assertFalse(saved.stats.contains("\"reason\""))
            val patch = HubStreamProtocol.decodeFreshness("""{"updatedAt":"new"}""")
            cache.save(envelope.copy(freshness = patch))
            val refreshed = requireNotNull(cache.read())
            assertEquals("new", HubProtocolParser.decodeStats(refreshed.stats).updatedAt)
            assertEquals(42L, HubProtocolParser.decodeStats(refreshed.stats).periods.getValue("today").totalTokens)
            assertNull(refreshed.freshness)
            cache.save(envelope.copy(stats = "not JSON"))
            assertEquals(refreshed, cache.read())
        } finally {
            cache.clear()
            context.filesDir.delete()
        }
    }

    @Test fun cacheHydratesOffMainAndPersistsAccumulatedFreshness() {
        val context = isolatedContext()
        val cache = SnapshotCache(context)
        val store = SecureConnectionStore(context)
        val patch = HubStreamProtocol.decodeFreshness("""{"updatedAt":"new"}""")
        cache.save(wire.copy(freshness = patch))
        store.save(HubConnection("http://100.64.0.1:17321", "synthetic-test", false))
        lateinit var repository: HubRepository
        try {
            instrumentation.runOnMainSync {
                repository = HubRepository(context) {
                    assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                    it.read()
                }
            }
            runBlocking { withTimeout(10_000) { requireNotNull(repository.cacheLoadJob).join() } }
            val snapshot = requireNotNull(repository.state.value.snapshot)
            assertEquals(42L, snapshot.today.totalTokens)
            assertEquals("new", snapshot.stats.updatedAt)
            assertTrue(snapshot.fromCache)
            assertTrue(snapshot.stale)
            assertEquals(HubProtocolParser.decodeStats(wire.copy(freshness = patch).resolvedStats), snapshot.stats)
        } finally {
            instrumentation.runOnMainSync { repository.close() }
            store.clear()
            cache.clear()
            context.filesDir.delete()
        }
    }

    @Test fun slowCacheDoesNotBlockMainOrRestoreDisconnectedData() {
        val context = isolatedContext()
        val store = SecureConnectionStore(context)
        store.save(HubConnection("http://100.64.0.1:17321", "synthetic-test", false))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        lateinit var repository: HubRepository
        try {
            instrumentation.runOnMainSync {
                repository = HubRepository(context) {
                    entered.countDown()
                    try { check(release.await(10, TimeUnit.SECONDS)); wire } finally { finished.countDown() }
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals("Constructor must return while disk loading is blocked", 1L, release.count)
            assertEquals(1L, finished.count)
            assertTrue(repository.state.value.hasConnection)
            assertNull(repository.state.value.snapshot)
            val loading = requireNotNull(repository.cacheLoadJob)
            instrumentation.runOnMainSync { repository.disconnect() }
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            runBlocking { withTimeout(10_000) { loading.join() } }
            assertFalse(repository.state.value.hasConnection)
            assertNull(repository.state.value.snapshot)
            assertNull(store.read())
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { repository.close() }
            store.clear()
            SnapshotCache(context).clear()
            context.filesDir.delete()
        }
    }

    @Test fun legacyPairingAndSnapshotsStayWithTheirHubAcrossSwitches() {
        val context = isolatedContext()
        val store = SecureConnectionStore(context)
        val old = HubConnection("http://100.64.0.1:17321", "synthetic-original", false)
        val second = HubConnection("https://hub.example.com", "synthetic-second", false, allowPublicHttps = true)
        val originalSession = WidgetRuntime.session
        val legacyCache = SnapshotCache(context, LEGACY_HUB_PROFILE)
        val allowLegacyHydration = CountDownLatch(1)
        var secondCache: SnapshotCache? = null
        var repository: HubRepository? = null
        try {
            // Reproduce the encrypted record written before the profile registry existed.
            store.save(old)
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey("token_monitor_hub_connection_v1", null)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            val encrypted = cipher.doFinal(store.encode(old).toByteArray(Charsets.UTF_8))
            val encoding = Base64.NO_WRAP or Base64.URL_SAFE
            context.getSharedPreferences("secure_connection", 0).edit(commit = true) {
                putString("iv", Base64.encodeToString(cipher.iv, encoding))
                putString("ciphertext", Base64.encodeToString(encrypted, encoding))
                remove("active_profile_id")
            }
            assertEquals(old, store.read())
            assertEquals(LEGACY_HUB_PROFILE, store.localActiveId())
            assertEquals(LEGACY_HUB_PROFILE, store.profiles().activeId)
            store.saveModelAliases("legacy aliases")
            legacyCache.save(wire)
            val originalRefresh = WidgetRefreshPreferences(context, LEGACY_HUB_PROFILE)
            originalRefresh.save(WidgetRefreshSettings(WidgetRefreshInterval.Thirty))
            originalRefresh.failure(1234, rejected = true)

            val id = requireNotNull(store.save(second, addedName = "Travel").activeId)
            assertEquals(listOf(LEGACY_HUB_PROFILE, id), store.profiles().entries.map { it.id })
            assertNotEquals(LEGACY_HUB_PROFILE, id)
            assertEquals(id, store.localActiveId())
            assertEquals(second, store.read())
            assertEquals(WidgetRefreshInterval.Off, WidgetRefreshPreferences(context).settings.interval)
            assertFalse(WidgetRefreshPreferences(context).record.secretRejected)
            assertNull(store.readModelAliases(id))
            assertFalse(context.getSharedPreferences("secure_connection", 0).all.values.any { "Travel" in it.toString() })
            val savedCache = SnapshotCache(context, id)
            secondCache = savedCache
            assertNull(savedCache.read())
            savedCache.save(wire.copy(stats = """{"periods":{"today":{"totalTokens":77}}}""", capturedAt = 9999))
            WidgetRuntime.session = WidgetSession(enabled = true, snapshot = HubSnapshot(capturedAt = 12345),
                profileId = LEGACY_HUB_PROFILE)
            assertEquals(77L, loadWidgetSnapshot(context).snapshot?.today?.totalTokens)

            instrumentation.runOnMainSync {
                repository = HubRepository(context) { cache ->
                    val snapshot = cache.read()
                    if (snapshot?.capturedAt == wire.capturedAt) {
                        check(allowLegacyHydration.await(10, TimeUnit.SECONDS))
                    }
                    snapshot
                }
            }
            val hub = requireNotNull(repository)
            runBlocking { withTimeout(10_000) { requireNotNull(hub.cacheLoadJob).join() } }
            assertEquals(77L, hub.state.value.snapshot?.today?.totalTokens)
            instrumentation.runOnMainSync {
                assertTrue(hub.switchHubProfile(LEGACY_HUB_PROFILE))
                assertNull(hub.state.value.snapshot)
            }
            allowLegacyHydration.countDown()
            runBlocking { withTimeout(10_000) { requireNotNull(hub.cacheLoadJob).join() } }
            assertEquals(42L, hub.state.value.snapshot?.today?.totalTokens)
            assertEquals(old, SecureConnectionStore(context).read())
            assertEquals("legacy aliases", store.readModelAliases())
            assertEquals(WidgetRefreshInterval.Thirty, WidgetRefreshPreferences(context).settings.interval)
            assertTrue(WidgetRefreshPreferences(context).record.secretRejected)
            instrumentation.runOnMainSync {
                hub.renameHubProfile(id, "Office ✓")
                hub.disconnect()
            }
            assertNull(hub.state.value.snapshot)
            assertNull(store.read())
            assertNull(store.localActiveId())
            assertEquals("Office ✓", store.profiles().entries.single().name)
            assertEquals(77L, HubProtocolParser.decodeStats(requireNotNull(savedCache.read()).stats).periods.getValue("today").totalTokens)
        } finally {
            allowLegacyHydration.countDown()
            instrumentation.runOnMainSync { repository?.close() }
            WidgetRuntime.session = originalSession
            store.clear()
            legacyCache.clear()
            secondCache?.clear()
            context.filesDir.delete()
        }
    }
}
