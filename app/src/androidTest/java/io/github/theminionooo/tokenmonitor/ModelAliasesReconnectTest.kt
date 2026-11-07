package io.github.theminionooo.tokenmonitor

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.data.protocol.decodeModelAliases
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ModelAliasesReconnectTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun dashboardFetchesAliasesOncePerRevisionAndWidgetsOnlyReadTheSavedCopy() = withHub { hub, server, owner ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.modelAliases?.revision == 1L }
        assertEquals(1, server.aliasReads.get())
        Thread.sleep(1200)
        assertEquals("Unchanged stream revisions cannot refetch settings", 1, server.aliasReads.get())
        server.revision = 2
        await(15_000) { hub.state.value.modelAliases?.revision == 2L }
        assertEquals(2, server.aliasReads.get())
        assertEquals(2L, decodeModelAliases(SecureConnectionStore(context).readModelAliases()!!)!!.revision)
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
        server.revision = 3
        instrumentation.runOnMainSync { hub.setWidgetActive(true, SystemClock.elapsedRealtime() + 60_000) }
        await { hub.state.value.widgetLiveActive && hub.state.value.snapshot?.stats?.modelAliasRevision == 3L }
        assertEquals("Widget Live cannot request aliases", 2, server.aliasReads.get())
        instrumentation.runOnMainSync { hub.setWidgetActive(false) }
        // A one-shot Refresh follows the same snapshot loader and cannot request aliases either.
        instrumentation.runOnMainSync { hub.setWidgetActive(true, SystemClock.elapsedRealtime() + 45_000) }
        await { hub.state.value.widgetLiveActive }
        assertEquals(2, server.aliasReads.get())
        assertTrue("Raw cache must preserve source IDs", SnapshotCache(context).read()!!.stats.contains("provider/gpt-5"))
        assertEquals("GET is the only allowed fixture method", 0, server.writes.get())
    }

    @Test fun aRestoredAliasRevisionDoesNotTriggerAnotherGet() = withHub(cachedAliases = true) { hub, server, owner ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.streamActive && hub.state.value.modelAliases?.revision == 1L }
        Thread.sleep(800)
        assertEquals("Restored settings already match the advertised revision", 0, server.aliasReads.get())
    }

    @Test fun olderHubsWithoutMarkersOrTheRouteKeepRawModelsWithoutAnError() {
        for (missingMarker in listOf(true, false)) withHub { hub, server, owner ->
            server.marker = !missingMarker
            server.aliasRoute = false
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await { hub.state.value.streamActive }
            if (!missingMarker) await { server.aliasReads.get() == 1 }
            Thread.sleep(1000)
            assertNull(hub.state.value.modelAliases)
            assertNull(hub.state.value.message)
            assertEquals(if (missingMarker) 0 else 1, server.aliasReads.get())
            assertEquals(setOf("provider/gpt-5", "gpt-5"), hub.state.value.snapshot!!.today.models.keys)
        }
    }

    @Test fun shortDropsStayQuietAndLongOutagesKeepOneNoticeUntilFreshDataArrives() = withHub { hub, server, owner ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.streamActive }
        server.drop = true
        await { server.streams.get() >= 2 && hub.state.value.streamActive }
        assertNull("A short interruption is quiet", hub.state.value.message)
        server.failReads = true
        val failedAt = SystemClock.elapsedRealtime()
        server.drop = true
        await { !hub.state.value.streamActive }
        while (SystemClock.elapsedRealtime() - failedAt < 2_800) {
            assertNull("No notice before three seconds", hub.state.value.message)
            assertEquals(100L, hub.state.value.snapshot?.today?.totalTokens)
            Thread.sleep(30)
        }
        await { hub.state.value.message != null }
        val notice = hub.state.value.message
        val requests = server.failedReads.get()
        await(15_000) { server.failedReads.get() > requests }
        assertEquals("Retries retain the same notice", notice, hub.state.value.message)
        server.failReads = false
        await(35_000) { hub.state.value.streamActive && hub.state.value.message == null }
    }

    @Test fun aliasReadsRejectRedirectsAndARejectedSecretStopsActiveWork() {
        for (status in listOf(302, 401)) withHub { hub, server, owner ->
            server.aliasStatus = status
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await { server.aliasReads.get() == 1 }
            if (status == 401) await { hub.state.value.secretRejected }
            else await { hub.state.value.streamActive }
            Thread.sleep(600)
            assertEquals(0, server.redirectedReads.get())
            assertEquals(0, server.credentialErrors.get())
            assertNull(hub.state.value.modelAliases)
            if (status == 401) {
                assertFalse(hub.state.value.streamActive)
                assertTrue(hub.state.value.message!!.contains("rejected the secret"))
                val count = server.streams.get()
                Thread.sleep(1200)
                assertEquals(count, server.streams.get())
            }
        }
    }

    @Test fun widgetsSharePreparedSavedAliasesAndMissingRevisionsKeepRawNames() = withHub { hub, server, owner ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.modelAliases?.revision == 1L }
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
        await { SnapshotCache(context).read()?.capturedAt == hub.state.value.snapshot?.capturedAt }
        val source = io.github.theminionooo.tokenmonitor.widget.loadWidgetSnapshot(context)
        val first = io.github.theminionooo.tokenmonitor.widget.loadWidgetDeck(context, -1, source)
        val second = io.github.theminionooo.tokenmonitor.widget.loadWidgetDeck(context, -1, source)
        assertEquals("GPT Five", first.data.models.single().name)
        assertEquals(100L, first.data.models.single().tokens)
        assertEquals(first.data.models, second.data.models)
        assertEquals(1, server.aliasReads.get())
        SnapshotCache(context).save(SnapshotCache(context).read()!!.copy(stats = """{"periods":{"today":{"models":{"provider/gpt-5":100}}}}"""))
        val raw = io.github.theminionooo.tokenmonitor.widget.loadWidgetSnapshot(context)
        assertEquals("provider/gpt-5", raw.modelNames.name("provider/gpt-5"))
    }

    @Test fun savedAliasesShareThePairingLifetimeAndDoNotSurviveAChangedHub() {
        val store = SecureConnectionStore(context)
        val pairing = HubConnection("http://100.88.1.2:17321", "synthetic-storage", false)
        try {
            store.save(pairing)
            store.saveModelAliases("saved display settings")
            store.save(pairing.copy(allowLocalNetwork = true), resetAliases = false)
            assertEquals("saved display settings", SecureConnectionStore(context).readModelAliases())
            store.save(pairing.copy(baseUrl = "http://100.88.1.3:17321"))
            assertNull(store.readModelAliases())
            store.saveModelAliases("settings")
            store.clear()
            assertNull(store.readModelAliases())
        } finally { store.clear() }
    }

    @Test fun liveNotificationUsesSessionsThenQuotaThenUsageAndKeepsStop() = withHub { hub, server, _ ->
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        server.reading = "sessions"
        context.startForegroundService(android.content.Intent(context,
            io.github.theminionooo.tokenmonitor.widget.WidgetLiveService::class.java)
            .setAction(io.github.theminionooo.tokenmonitor.widget.WidgetLiveService.ACTION_LIVE))
        fun notification() = manager.activeNotifications.firstOrNull { it.id == 71 }?.notification
        fun headline() = notification()?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        fun capture(label: String) {
            if (InstrumentationRegistry.getArguments().getString("capture_live") == null) return
            val automation = instrumentation.uiAutomation
            automation.executeShellCommand("cmd statusbar expand-notifications").close()
            Thread.sleep(1000)
            val bitmap = checkNotNull(automation.takeScreenshot())
            context.openFileOutput("live-$label.png", 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            automation.executeShellCommand("cmd statusbar collapse").close()
        }
        try {
            await { headline() == "1 running · 1 waiting" }
            assertTrue(notification()!!.actions.any { it.title == "Stop" })
            capture("sessions")
            server.reading = "quota"
            instrumentation.runOnMainSync { hub.refreshNow() }
            await { headline().contains("5% left") }
            capture("quota")
            server.reading = "today"
            instrumentation.runOnMainSync { hub.refreshNow() }
            await { headline().contains("100 tokens") }
            capture("usage")
            assertEquals("Live never requests aliases", 0, server.aliasReads.get())
        } finally {
            instrumentation.runOnMainSync { io.github.theminionooo.tokenmonitor.widget.WidgetLiveService.stop(context) }
        }
        await { notification() == null }
    }

    private fun withHub(cachedAliases: Boolean = false, test: (HubRepository, Fixture, Any) -> Unit) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        Fixture().use { server ->
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
            SnapshotCache(context).clear()
            SecureConnectionStore(context).save(HubConnection("http://$address:${server.port}", "synthetic-test", true))
            if (cachedAliases) SecureConnectionStore(context).saveModelAliases(
                """{"ok":true,"version":1,"revision":1,"value":{"modelAliases":{},"modelAliasGrouping":"off"}}""")
            lateinit var hub: HubRepository
            val owner = Any()
            instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
            try { test(hub, server, owner) } finally {
                instrumentation.runOnMainSync {
                    hub.setWidgetActive(false)
                    hub.setDashboardVisible(owner, false)
                    hub.disconnect()
                    HubRepositoryPool.release(hub)
                }
            }
        }
    }

    private fun await(timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Condition did not become true in $timeout ms", condition())
    }

    private class Fixture : AutoCloseable {
        private val server = ServerSocket(0)
        val port get() = server.localPort
        val aliasReads = AtomicInteger()
        val writes = AtomicInteger()
        val redirectedReads = AtomicInteger()
        val credentialErrors = AtomicInteger()
        val streams = AtomicInteger()
        val failedReads = AtomicInteger()
        @Volatile var marker = true
        @Volatile var aliasRoute = true
        @Volatile var aliasStatus = 200
        @Volatile var revision = 1
        @Volatile var failReads = false
        @Volatile var drop = false
        @Volatile var reading = "today"
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val acceptor = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets.add(socket)
                thread(isDaemon = true) {
                    try { socket.use(::respond) } catch (_: Exception) {
                        // Closing the fixture or repository also closes held stream sockets.
                    } finally { sockets.remove(socket) }
                }
            }
        }

        private fun stats(): String {
            val now = java.time.Instant.now().toString()
            val sessions = if (reading == "sessions") listOf("main" to "running", "child" to "running", "waiting" to "waiting")
                .joinToString(",") { (id, state) ->
                    "\"$id\":{\"sessionId\":\"$id\",\"client\":\"codex\",\"totalTokens\":1,\"lastUsedAt\":\"$now\",\"turnEnded\":true," +
                        (if (id == "child") "\"parentSessionId\":\"main\"," else "") +
                        "\"liveActivity\":{\"state\":\"$state\",\"observedAt\":\"$now\"}}"
                } else ""
            val limits = if (reading != "today") """"limits":{"providers":[{"provider":"codex","status":"ok","windows":[{"kind":"session","label":"Session","remainingPercent":5,"showMeter":true}]}]},""" else ""
            return """{${if (marker) "\"syncSettingsRevisions\":{\"modelAliases\":$revision}," else ""}"updatedAt":"$now",$limits"periods":{"today":{"totalTokens":100,"models":{"provider/gpt-5":60,"gpt-5":40},"sessions":{$sessions}}}}"""
        }

        private fun respond(socket: Socket) {
            socket.soTimeout = 5000
            val reader = socket.getInputStream().bufferedReader()
            val request = reader.readLine().split(' ')
            if (request[0] != "GET") writes.incrementAndGet()
            val path = request[1]
            val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
            if (path == "/api/sync/settings/modelAliases" && headers.none { it.equals("Authorization: Bearer synthetic-test", ignoreCase = true) }) credentialErrors.incrementAndGet()
            if (path == "/api/redirect-target") redirectedReads.incrementAndGet()
            val out = socket.getOutputStream()
            if (path.endsWith("/stream")) {
                streams.incrementAndGet()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".toByteArray())
                while (!drop && !socket.isClosed) {
                    out.write("event: stats\ndata: {\"stats\":${stats()}}\n\n".toByteArray())
                    out.flush()
                    Thread.sleep(250)
                }
                drop = false
                return
            }
            var status = 200
            val body = if (failReads) {
                failedReads.incrementAndGet()
                status = 503
                "{}"
            } else when (path) {
                "/api/health" -> """{"ok":true,"role":"hub"}"""
                "/api/stats" -> stats()
                "/api/sync/settings/modelAliases" -> {
                    aliasReads.incrementAndGet()
                    status = if (aliasRoute) aliasStatus else 404
                    """{"ok":true,"version":1,"revision":$revision,"value":{"modelAliases":{"gpt-5":"GPT Five","provider/gpt-5":"GPT Five"},"modelAliasGrouping":"duplicates"}}"""
                }
                else -> "{}"
            }
            val bytes = body.toByteArray()
            out.write("HTTP/1.1 $status Status\r\n${if (status == 302) "Location: /api/redirect-target\r\n" else ""}Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }

        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1000)
        }
    }
}
