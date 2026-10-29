package io.github.theminionooo.tokenmonitor

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.widget.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Actual repository and sockets, with a disposable pairing and synthetic data in the preview app. */
class WidgetBackgroundRunTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }

    @Test fun scheduledReadKeepsSavedDataOnRejectionAndDoesNotRetryTheSecret() = fixture { hub, prefs, reads, mode ->
        runBlocking { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } }
        assertEquals(listOf("/api/health", "/api/stats", "/api/history"), reads.toList())
        assertEquals(42L, hub.state.value.snapshot?.today?.totalTokens)
        val first = requireNotNull(hub.state.value.snapshot)
        assertTrue(prefs.record.receivedBytes > 0)
        val savedDeadline = System.currentTimeMillis() + 5_000
        while (SnapshotCache(context).read()?.capturedAt != first.capturedAt && System.currentTimeMillis() < savedDeadline) Thread.sleep(20)
        assertEquals(first.capturedAt, SnapshotCache(context).read()?.capturedAt)
        assertFalse(loadWidgetSnapshot(context).snapshot?.stale ?: true)
        assertNull(loadWidgetSnapshot(context).session.note)
        mode.reject = true
        reads.clear()
        runBlocking { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } }
        assertEquals(listOf("/api/health", "/api/stats"), reads.toList())
        assertEquals(first, hub.state.value.snapshot)
        assertTrue(prefs.record.secretRejected)
        assertTrue(prefs.record.stale(first.capturedAt))
        assertTrue(loadWidgetSnapshot(context).snapshot?.stale == true)
        assertNull(loadWidgetSnapshot(context).session.note)
        reads.clear()
        runBlocking { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } }
        assertTrue(reads.isEmpty())
    }

    @Test fun blockingReadEndsAtTheFortyFiveSecondCapAndLeavesNoSnapshot() = fixture { hub, prefs, _, mode ->
        mode.block = true
        val started = android.os.SystemClock.elapsedRealtime()
        runBlocking { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } }
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        assertTrue("The quiet run exceeded its cap: $elapsed ms", elapsed in 44_000..49_000)
        assertNull(hub.state.value.snapshot)
        assertTrue(prefs.record.failureAt > 0)
    }

    @Test fun liveSessionSkipsWithoutChangingFailureOrSnapshot() = fixture { hub, prefs, reads, _ ->
        val before = WidgetRuntime.session
        try {
            WidgetRuntime.session = WidgetSession(enabled = true)
            runBlocking { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } }
            assertTrue(reads.isEmpty())
            assertEquals(0L, prefs.record.failureAt)
        } finally { WidgetRuntime.session = before }
    }

    @Test fun appliedSnapshotCompletesWithoutWaitingForTheDiskQueue() = fixture { hub, prefs, _, mode ->
        mode.blockCache = true
        val started = android.os.SystemClock.elapsedRealtime()
        runBlocking { withTimeout(5000) { withContext(Dispatchers.Main) { hub.refreshWidgetInBackground() } } }
        assertTrue(android.os.SystemClock.elapsedRealtime() - started < 5000)
        assertEquals(42L, hub.state.value.snapshot?.today?.totalTokens)
        assertTrue(prefs.record.successAt > 0)
        assertEquals(0L, prefs.record.failureAt)
        assertTrue(prefs.record.completed)
        mode.cacheRelease.countDown()
        assertTrue(mode.cacheSaved.await(5, TimeUnit.SECONDS))
    }

    private class Mode {
        @Volatile var reject = false
        @Volatile var block = false
        @Volatile var blockCache = false
        val cacheRelease = CountDownLatch(1)
        val cacheSaved = CountDownLatch(1)
    }

    private fun fixture(checks: (HubRepository, WidgetRefreshPreferences, MutableList<String>, Mode) -> Unit) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        val prefs = WidgetRefreshPreferences(context)
        val pairing = SecureConnectionStore(context)
        val host = AppWidgetHost(context, 7805)
        val user = shell("am get-current-user")
        val reads = Collections.synchronizedList(mutableListOf<String>())
        val mode = Mode()
        val originalSession = WidgetRuntime.session
        val release = CountDownLatch(1)
        val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        lateinit var repository: HubRepository
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) {
                        runCatching { socket.use {
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine().split(' ')[1]
                            val headers = mutableListOf<String>()
                            while (true) { val line = reader.readLine(); if (line.isNullOrEmpty()) break; headers += line }
                            reads += path
                            check(headers.any { h -> h.startsWith("Authorization:", true) } == (path != "/api/health"))
                            if (path == "/api/health" && mode.block) Thread.sleep(20_000)
                            if (path == "/api/stats" && mode.block) release.await(60, TimeUnit.SECONDS)
                            val rejected = path == "/api/stats" && mode.reject
                            val body = when (path) {
                                "/api/health" -> """{"ok":true,"role":"hub"}"""
                                "/api/stats" -> """{"periods":{"today":{"totalTokens":42}}}"""
                                else -> "{}"
                            }.toByteArray()
                            it.getOutputStream().write(("HTTP/1.1 ${if (rejected) "401 Unauthorized" else "200 OK"}\r\n" +
                                "Content-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                        } }
                    }
                }
            }
            try {
                prefs.preferences.edit().clear().commit()
                SnapshotCache(context).clear()
                pairing.save(HubConnection("http://$address:${server.localPort}", "synthetic-background-secret", true))
                prefs.save(WidgetRefreshSettings(WidgetRefreshInterval.Thirty, false))
                WidgetRuntime.session = WidgetSession(note = "Earlier widget refresh failed")
                shell("appwidget grantbind --package ${context.packageName} --user $user")
                val id = host.allocateAppWidgetId()
                assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id, ComponentName(context, SwipeWidgetProvider::class.java)))
                instrumentation.runOnMainSync {
                    repository = HubRepository(context, saveCache = { cache, wire ->
                        if (mode.blockCache) check(mode.cacheRelease.await(10, TimeUnit.SECONDS))
                        cache.save(wire)
                        mode.cacheSaved.countDown()
                    })
                }
                checks(repository, prefs, reads, mode)
            } finally {
                release.countDown()
                mode.cacheRelease.countDown()
                instrumentation.runOnMainSync { repository.close() }
                prefs.save(WidgetRefreshSettings())
                WidgetRuntime.session = originalSession
                pairing.clear()
                host.deleteHost()
                WidgetRefreshScheduler.reconcile(context)
                shell("appwidget revokebind --package ${context.packageName} --user $user")
                server.close()
                worker.join(1000)
            }
        }
    }
}
