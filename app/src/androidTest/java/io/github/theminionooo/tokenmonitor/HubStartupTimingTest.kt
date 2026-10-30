package io.github.theminionooo.tokenmonitor

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.network.*
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class HubStartupTimingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun timingsIdentifyTheSlowOptionalHistoryReadBeforeFreshPublication() = withHub(historyDelayMs = 900) { server, hub, samples ->
        val owner = Any()
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.streamActive && samples.any { it.stage == HubTimingStage.STREAM_OPEN } }
        val requests = samples.filter { it.stage == HubTimingStage.REQUEST }
        assertEquals(HubTimingEndpoint.HEALTH, requests.first().endpoint)
        assertEquals(setOf(HubTimingEndpoint.HEALTH, HubTimingEndpoint.STATS, HubTimingEndpoint.DEVICES,
            HubTimingEndpoint.HISTORY, HubTimingEndpoint.SUBSCRIPTIONS), requests.map { it.endpoint }.toSet())
        assertEquals(5, requests.size)
        val history = requests.single { it.endpoint == HubTimingEndpoint.HISTORY }
        assertTrue("The injected delay must be visible in request timing", history.durationUs >= 850_000)
        assertTrue(samples.single { it.stage == HubTimingStage.PUBLISH }.elapsedUs >= history.elapsedUs)
        assertEquals(1234L, hub.state.value.snapshot?.today?.totalTokens)
        assertEquals(HubTimingOutcome.OK, history.outcome)
        assertTrue(requests.all { it.status == 200 && it.bytes > 0 })
        assertTrue(server.healthWasAnonymous)
        assertNoPrivateData(samples, server)
        writeSamples("slow-history", samples)
    }

    @Test fun pausingDuringParallelReadsDoesNotPublishOrContinueRequests() = withHub(statsDelayMs = 1200) { server, hub, samples ->
        val owner = Any()
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { server.started.contains("/api/stats") }
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
        Thread.sleep(150)
        val requestsAfterStop = server.started.size
        Thread.sleep(1350)
        assertNull(hub.state.value.snapshot)
        assertEquals(requestsAfterStop, server.started.size)
        assertFalse(server.started.contains("/api/stats/stream"))
        assertFalse(samples.any { it.stage == HubTimingStage.PUBLISH })
        assertNoPrivateData(samples, server)
        writeSamples("paused-read", samples)
    }

    @Test fun rejectedHealthStopsBeforeCredentialsAndStillRecordsTheFailure() = withHub(healthStatus = 401) { server, hub, samples ->
        val owner = Any()
        instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
        await { hub.state.value.secretRejected }
        assertEquals(listOf("/api/health"), server.started.toList())
        assertTrue(server.healthWasAnonymous)
        val request = samples.single { it.stage == HubTimingStage.REQUEST }
        assertEquals(401, request.status)
        assertEquals(HubTimingOutcome.HTTP_ERROR, request.outcome)
        assertNoPrivateData(samples, server)
    }

    @Test fun rejectedParallelDetailStopsTheSnapshotAndStream() = withHub(statsDelayMs = 1200, rejectDevices = true) { server, hub, samples ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(Any(), true) }
        await { hub.state.value.secretRejected }
        assertNull(hub.state.value.snapshot)
        assertFalse(server.started.contains("/api/stats/stream"))
        assertFalse(samples.any { it.stage == HubTimingStage.PUBLISH })
        assertTrue(samples.any { it.stage == HubTimingStage.REQUEST && it.endpoint == HubTimingEndpoint.DEVICES && it.status == 401 })
        assertNoPrivateData(samples, server)
    }

    @Test fun measuresStartupWithEqualNetworkLatency() = withHub(latencyMs = 200) { server, hub, samples ->
        instrumentation.runOnMainSync { hub.setDashboardVisible(Any(), true) }
        await { hub.state.value.streamActive && samples.any { it.stage == HubTimingStage.STREAM_OPEN } }
        val parallel = InstrumentationRegistry.getArguments().getString("expect_parallel", "true").toBoolean()
        assertEquals(if (parallel) 2 else 1, server.peakWork.get())
        assertEquals(1234L, hub.state.value.snapshot?.today?.totalTokens)
        assertEquals(5, samples.count { it.stage == HubTimingStage.REQUEST })
        assertEquals("/api/health", server.started.first())
        assertNoPrivateData(samples, server)
        writeSamples("latency-benchmark", samples)
    }

    private fun withHub(historyDelayMs: Long = 0, statsDelayMs: Long = 0, healthStatus: Int = 200,
                        latencyMs: Long = 0, rejectDevices: Boolean = false,
                        test: (FixtureHub, HubRepository, CopyOnWriteArrayList<HubTimingSample>) -> Unit) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub(historyDelayMs, statsDelayMs, healthStatus, latencyMs, rejectDevices).use { server ->
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
            server.baseUrl = "http://$address:${server.port}"
            SecureConnectionStore(context).save(HubConnection(server.baseUrl, SECRET, true))
            val samples = CopyOnWriteArrayList<HubTimingSample>()
            val timing = HubTimingRecorder(true, sink = samples::add)
            lateinit var hub: HubRepository
            instrumentation.runOnMainSync {
                hub = HubRepository(context, timing = timing, saveCache = { _, _ -> }, loadCache = { null })
            }
            try { test(server, hub, samples) } finally {
                instrumentation.runOnMainSync { hub.close() }
                SecureConnectionStore(context).clear()
                WidgetRefreshPreferences(context).clearRecord()
            }
        }
    }

    private fun assertNoPrivateData(samples: List<HubTimingSample>, server: FixtureHub) {
        val text = samples.joinToString("\n") { it.line() }
        assertFalse(text.contains(SECRET))
        assertFalse(text.contains(server.baseUrl))
        assertFalse(text.contains("periods"))
    }

    private fun writeSamples(name: String, samples: List<HubTimingSample>) {
        val folder = context.filesDir.resolve("performance").also { it.mkdirs() }
        folder.resolve("hub-startup-$name.log").writeText(samples.joinToString("\n") { it.line() })
    }

    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < until) Thread.sleep(20)
        assertTrue("Fixture did not reach its expected state", condition())
    }

    private class FixtureHub(private val historyDelayMs: Long, private val statsDelayMs: Long, private val healthStatus: Int,
                             private val latencyMs: Long, private val rejectDevices: Boolean) : AutoCloseable {
        private val server = ServerSocket(0)
        val port get() = server.localPort
        lateinit var baseUrl: String
        val started = CopyOnWriteArrayList<String>()
        @Volatile var healthWasAnonymous = false
        private val activeWork = AtomicInteger()
        val peakWork = AtomicInteger()
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val acceptor = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets.add(socket)
                thread(isDaemon = true) {
                    try { socket.use { respond(it) } } catch (_: Exception) { /* The test closes active reads. */ }
                    finally { sockets.remove(socket) }
                }
            }
        }

        private fun respond(socket: Socket) {
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader()
            val path = reader.readLine().split(' ')[1]
            var authenticated = false
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Authorization:", true)) authenticated = true
            }
            if (path == "/api/health") healthWasAnonymous = !authenticated
            started.add(path)
            val output = socket.getOutputStream()
            if (path == "/api/stats/stream") {
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".toByteArray())
                output.flush()
                while (!server.isClosed) { output.write(": heartbeat\n\n".toByteArray()); output.flush(); Thread.sleep(200) }
                return
            }
            val active = activeWork.incrementAndGet()
            peakWork.updateAndGet { maxOf(it, active) }
            try {
                Thread.sleep(latencyMs + when (path) { "/api/history" -> historyDelayMs; "/api/stats" -> statsDelayMs; else -> 0 })
            } finally { activeWork.decrementAndGet() }
            val body = when (path) {
                "/api/health" -> """{"ok":true,"role":"hub"}"""
                "/api/stats" -> """{"periods":{"today":{"totalTokens":1234}}}"""
                else -> "{}"
            }.toByteArray()
            val status = when {
                path == "/api/health" -> healthStatus
                rejectDevices && path == "/api/devices" -> 401
                else -> 200
            }
            output.write("HTTP/1.1 $status Fixture\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(body)
            output.flush()
        }

        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1000)
        }
    }

    companion object { private const val SECRET = "synthetic-startup-secret" }
}
