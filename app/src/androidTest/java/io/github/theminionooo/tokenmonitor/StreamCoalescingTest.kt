package io.github.theminionooo.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCoalescingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun deeplyNestedStreamEndsNormallyAndTheDashboardRecovers() = withHub(FixtureMode.DeepOnce) { server ->
        val owner = Any()
        lateinit var hub: HubRepository
        instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
        try {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await(20_000) { server.openedAt.size >= 2 && hub.state.value.streamActive && hub.state.value.snapshot?.today?.totalTokens == SNAPSHOT_TOKENS }
            Thread.sleep(1500)
            assertEquals("The valid replacement stream should remain open", 2, server.openedAt.size)
            assertTrue(hub.state.value.streamActive)
        } finally {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false); HubRepositoryPool.release(hub) }
        }
    }

    @Test fun repeatedSnapshotDisconnectsBackOffAndPausingStopsRetries() = withHub(FixtureMode.DropAfterSnapshot) { server ->
        val owner = Any()
        lateinit var hub: HubRepository
        instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
        try {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await(30_000) { server.openedAt.size >= 4 }
            val times = synchronized(server.openedAt) { server.openedAt.toList() }
            assertTrue("The third retry must retain exponential backoff: $times", times[3] - times[2] >= 2800)
            assertEquals(SNAPSHOT_TOKENS, hub.state.value.snapshot?.today?.totalTokens)
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
            val count = server.openedAt.size
            Thread.sleep(2000)
            assertEquals(count, server.openedAt.size)
        } finally {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false); HubRepositoryPool.release(hub) }
        }
    }

    @Test fun rapidCompleteUpdatesApplyAtMostOneEveryFiveSecondsAndTheLatestArrives() = withHub { server ->
        val owner = Any()
        lateinit var hub: HubRepository
        instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
        try {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await(10_000) { hub.state.value.streamActive && hub.state.value.snapshot?.today?.totalTokens == SNAPSHOT_TOKENS }
            val seen = linkedSetOf<Long>()
            val deadline = System.nanoTime() + 16_000_000_000L
            while (System.nanoTime() < deadline) {
                hub.state.value.snapshot?.today?.totalTokens?.let { seen += it }
                if (server.finished.get() == 1 && seen.last() == server.lastSent.get().toLong()) break
                Thread.sleep(10)
            }
            val updates = seen.filter { it > SNAPSHOT_TOKENS }
            assertEquals("The newest complete update must arrive after the burst", server.lastSent.get().toLong(), seen.last())
            // 40 complete events over eight seconds: the first applies at once, then at most one per five seconds.
            assertTrue("Applied ${updates.size} of ${STATS_EVENTS} complete updates: $updates", updates.size in 2..3)
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
            await(10_000) { server.streams.get() == 0 }
        } finally {
            instrumentation.runOnMainSync {
                hub.setDashboardVisible(owner, false)
                HubRepositoryPool.release(hub)
            }
        }
    }

    @Test fun pausingDropsAHeldUpdate() = withHub { server ->
        val owner = Any()
        lateinit var hub: HubRepository
        instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
        try {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await(10_000) { (hub.state.value.snapshot?.today?.totalTokens ?: 0) > SNAPSHOT_TOKENS }
            // The first complete update applied at once; the following ones are held for the interval.
            Thread.sleep(1_000)
            val beforePause = hub.state.value.snapshot!!.today.totalTokens
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
            await(10_000) { server.streams.get() == 0 }
            Thread.sleep(6_000)
            assertEquals("A paused dashboard must not apply a held update", beforePause, hub.state.value.snapshot!!.today.totalTokens)
        } finally {
            instrumentation.runOnMainSync {
                hub.setDashboardVisible(owner, false)
                HubRepositoryPool.release(hub)
            }
        }
    }

    private fun withHub(mode: FixtureMode = FixtureMode.Burst, test: (FixtureHub) -> Unit) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub(mode).use { server ->
            val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
            SnapshotCache(context).clear()
            SecureConnectionStore(context).save(HubConnection("http://$address:${server.port}", "synthetic-test", true))
            try { test(server) } finally {
                SecureConnectionStore(context).clear()
                SnapshotCache(context).clear()
            }
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Condition did not become true within $timeoutMs ms", condition())
    }

    /** Sends one snapshot, then a burst of complete stats events 200 ms apart, then keeps the stream open. */
    private enum class FixtureMode { Burst, DeepOnce, DropAfterSnapshot }

    private class FixtureHub(private val mode: FixtureMode) : AutoCloseable {
        private val server = ServerSocket(0)
        val port get() = server.localPort
        val streams = AtomicInteger()
        val openedAt = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val lastSent = AtomicInteger(SNAPSHOT_TOKENS.toInt())
        val finished = AtomicInteger()
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val acceptor = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets.add(socket)
                thread(isDaemon = true) {
                    try { socket.use { respond(it) } } catch (_: Exception) {
                        // Closing the repository or fixture closes an active stream.
                    } finally { sockets.remove(socket) }
                }
            }
        }

        private fun stats(count: Long) = """{"updatedAt":"$count","periods":{"today":{"totalTokens":$count}}}"""

        private fun respond(socket: Socket) {
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader()
            val path = reader.readLine().split(' ')[1]
            while (!reader.readLine().isNullOrEmpty()) { /* Consume headers. */ }
            val output = socket.getOutputStream()
            if (path == "/api/stats/stream") {
                streams.incrementAndGet()
                openedAt.add(android.os.SystemClock.elapsedRealtime())
                try {
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".toByteArray())
                    if (mode == FixtureMode.DeepOnce && openedAt.size == 1) {
                        output.write(("event: snapshot\ndata: {\"stats\":{\"unknown\":" + "[".repeat(10_000) + "0" + "]".repeat(10_000) + "}}\n\n").toByteArray())
                        output.flush()
                        return
                    }
                    output.write("event: snapshot\ndata: {\"stats\":${stats(SNAPSHOT_TOKENS)}}\n\n".toByteArray())
                    output.flush()
                    if (mode == FixtureMode.DropAfterSnapshot) return
                    Thread.sleep(500)
                    for (index in 1..(if (mode == FixtureMode.Burst) STATS_EVENTS else 0)) {
                        val tokens = SNAPSHOT_TOKENS + index
                        output.write("event: stats\ndata: {\"stats\":${stats(tokens)}}\n\n".toByteArray())
                        output.flush()
                        lastSent.set(tokens.toInt())
                        Thread.sleep(200)
                    }
                    finished.set(1)
                    while (!server.isClosed) {
                        output.write(": hb\n\n".toByteArray())
                        output.flush()
                        Thread.sleep(500)
                    }
                } finally { streams.decrementAndGet() }
            } else {
                val body = when (path) {
                    "/api/health" -> "{\"ok\":true,\"role\":\"hub\"}"
                    "/api/stats" -> stats(SNAPSHOT_TOKENS)
                    else -> "{}"
                }.toByteArray()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(body)
                output.flush()
            }
        }

        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1000)
        }
    }

    private companion object {
        const val SNAPSHOT_TOKENS = 1_000L
        const val STATS_EVENTS = 40
    }
}
