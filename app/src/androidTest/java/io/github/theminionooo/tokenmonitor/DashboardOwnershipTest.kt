package io.github.theminionooo.tokenmonitor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.ui.DashboardDestination
import io.github.theminionooo.tokenmonitor.ui.DashboardViewModel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class DashboardOwnershipTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun clearingOldDashboardKeepsReplacementLiveUntilItsLastPause() = withHub { server ->
        val oldStore = ViewModelStore()
        val newStore = ViewModelStore()
        lateinit var old: DashboardViewModel
        lateinit var current: DashboardViewModel
        try {
            instrumentation.runOnMainSync {
                old = DashboardViewModel(context.applicationContext as Application)
                oldStore.put("dashboard", old)
                old.onResume()
            }
            await { old.hubState.value.streamActive && server.streams.get() == 1 }
            instrumentation.runOnMainSync {
                current = DashboardViewModel(context.applicationContext as Application)
                newStore.put("dashboard", current)
                current.onResume()
                oldStore.clear()
                assertTrue("Old screen cleanup must not stop the replacement's stream", current.hubState.value.streamActive)
            }
            assertEquals("Both dashboards must share the original stream", 1, server.opened.get())
            val before = current.hubState.value.snapshot!!
            await { current.hubState.value.snapshot!!.today.totalTokens > before.today.totalTokens }
            assertSame("Complete stream updates must reuse unchanged month data", before.month, current.hubState.value.snapshot!!.month)
            instrumentation.runOnMainSync { current.onPause() }
            await { server.streams.get() == 0 }
            val requests = server.requests.get()
            Thread.sleep(500)
            assertEquals("The final pause must stop all requests", requests, server.requests.get())
            assertFalse(current.hubState.value.streamActive)
        } finally {
            instrumentation.runOnMainSync { oldStore.clear(); newStore.clear() }
        }
    }

    @Test fun settingsAndPausedRefreshCannotStopOrAcquireAnotherDashboardsStream() = withHub { server ->
        val oldStore = ViewModelStore()
        val currentStore = ViewModelStore()
        lateinit var old: DashboardViewModel
        lateinit var current: DashboardViewModel
        try {
            instrumentation.runOnMainSync {
                old = DashboardViewModel(context.applicationContext as Application)
                current = DashboardViewModel(context.applicationContext as Application)
                oldStore.put("dashboard", old)
                currentStore.put("dashboard", current)
                old.onResume()
                current.onResume()
            }
            await { current.hubState.value.streamActive && server.streams.get() == 1 }
            instrumentation.runOnMainSync {
                current.onResume()
                old.onPause()
                assertTrue("A late pause cannot release the replacement's lease", current.hubState.value.streamActive)
                old.onResume()
                current.choose(DashboardDestination.Settings)
                current.refresh()
                assertTrue("Settings cannot release the other screen's lease", old.hubState.value.streamActive)
            }
            assertEquals(1, server.opened.get())
            instrumentation.runOnMainSync { old.onPause() }
            await { server.streams.get() == 0 }
            val idleRequests = server.requests.get()
            instrumentation.runOnMainSync { current.refresh() }
            Thread.sleep(300)
            assertEquals("Settings Refresh cannot start networking", idleRequests, server.requests.get())
            instrumentation.runOnMainSync { current.choose(DashboardDestination.Home) }
            await { current.hubState.value.streamActive && server.streams.get() == 1 }
            val opened = server.opened.get()
            instrumentation.runOnMainSync { current.refresh() }
            await { current.hubState.value.streamActive && server.opened.get() == opened + 1 && server.streams.get() == 1 }
            instrumentation.runOnMainSync { current.onPause(); current.refresh() }
            await { server.streams.get() == 0 }
            val pausedRequests = server.requests.get()
            Thread.sleep(300)
            assertEquals("Paused Refresh cannot start networking", pausedRequests, server.requests.get())
        } finally {
            instrumentation.runOnMainSync { oldStore.clear(); currentStore.clear() }
        }
    }

    @Test fun refreshRestoresAnIdleDashboardWithOneSnapshotAndOneStream() = withHub { server ->
        val owner = Any()
        lateinit var hub: io.github.theminionooo.tokenmonitor.data.HubRepository
        instrumentation.runOnMainSync { hub = HubRepositoryPool.acquire(context) }
        try {
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, true) }
            await { hub.state.value.streamActive && server.streams.get() == 1 }
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
            await { server.streams.get() == 0 }
            val reads = server.statsReads.get()
            val opened = server.opened.get()
            instrumentation.runOnMainSync { hub.refreshDashboard(owner) }
            await { hub.state.value.streamActive && server.streams.get() == 1 }
            Thread.sleep(300)
            assertEquals("Recovery must fetch one full snapshot", reads + 1, server.statsReads.get())
            assertEquals("Recovery must open one stream", opened + 1, server.opened.get())
            instrumentation.runOnMainSync { hub.setDashboardVisible(owner, false) }
            await { server.streams.get() == 0 }
        } finally {
            instrumentation.runOnMainSync {
                hub.setDashboardVisible(owner, false)
                HubRepositoryPool.release(hub)
            }
        }
    }

    @Test fun switchingProfilesClosesTheOldStreamAndUsesTheSelectedSecret() {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub(requiredSecret = "fixture-one", tokenBase = 100).use { first ->
            FixtureHub(requiredSecret = "fixture-two", tokenBase = 900, sendSingleStatsUpdate = true).use { second ->
                val address = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
                val store = SecureConnectionStore(context)
                val firstId = requireNotNull(store.save(HubConnection("http://$address:${first.port}", "fixture-one", true)).activeId)
                val secondId = requireNotNull(store.save(HubConnection("http://$address:${second.port}", "fixture-two", true), addedName = "Second").activeId)
                store.activate(firstId)
                val owner = Any()
                lateinit var hub: io.github.theminionooo.tokenmonitor.data.HubRepository
                instrumentation.runOnMainSync {
                    hub = HubRepositoryPool.acquire(context)
                    hub.setDashboardVisible(owner, true)
                }
                try {
                    await { first.streams.get() == 1 && hub.state.value.streamActive }
                    assertEquals(0, second.opened.get())
                    instrumentation.runOnMainSync { assertTrue(hub.switchHubProfile(secondId)) }
                    await { first.streams.get() == 0 && second.streams.get() == 1 && hub.state.value.streamActive }
                    assertEquals(secondId, hub.state.value.activeProfileId)
                    // The new Hub sends one queued stats update and then only comments.
                    await { hub.state.value.snapshot?.today?.totalTokens == 902L }
                    assertEquals(1, first.opened.get())
                    assertEquals(1, second.opened.get())
                } finally {
                    instrumentation.runOnMainSync {
                        hub.setDashboardVisible(owner, false)
                        HubRepositoryPool.release(hub)
                    }
                    SnapshotCache(context, firstId).clear()
                    SnapshotCache(context, secondId).clear()
                    store.clear()
                }
            }
        }
    }

    private fun withHub(test: (FixtureHub) -> Unit) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub().use { server ->
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

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("Dashboard condition did not become true within ten seconds", condition())
    }

    private inner class FixtureHub(
        private val requiredSecret: String? = null,
        private val tokenBase: Int = 100,
        private val sendSingleStatsUpdate: Boolean = false,
    ) : AutoCloseable {
        private val server = ServerSocket(0)
        val port get() = server.localPort
        val requests = AtomicInteger()
        val statsReads = AtomicInteger()
        val streams = AtomicInteger()
        val opened = AtomicInteger()
        private val tokens = AtomicInteger(tokenBase)
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

        private fun stats(count: Int) = """{"updatedAt":"$count","periods":{"today":{"totalTokens":$count},"month":{"sessions":{"s":{"client":"codex","title":"Task","totalTokens":10}}}}}"""

        private fun respond(socket: Socket) {
            socket.soTimeout = 5_000
            val reader = socket.getInputStream().bufferedReader()
            val path = reader.readLine().split(' ')[1]
            var authorization: String? = null
            while (true) {
                val header = reader.readLine()?.takeUnless { it.isEmpty() } ?: break
                if (header.substringBefore(':').equals("Authorization", ignoreCase = true)) {
                    authorization = header.substringAfter(':').trim()
                }
            }
            requests.incrementAndGet()
            val output = socket.getOutputStream()
            if (path != "/api/health" && requiredSecret != null && authorization != "Bearer $requiredSecret") {
                output.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                return
            }
            if (path == "/api/stats/stream") {
                streams.incrementAndGet()
                opened.incrementAndGet()
                try {
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n".toByteArray())
                    if (sendSingleStatsUpdate) {
                        output.write("event: snapshot\ndata: {\"stats\":${stats(tokenBase + 1)}}\n\n".toByteArray())
                        output.write("event: stats\ndata: {\"stats\":${stats(tokenBase + 2)}}\n\n".toByteArray())
                    }
                    while (!server.isClosed) {
                        val frame = if (sendSingleStatsUpdate) ": keepalive\n\n"
                        else "event: snapshot\ndata: {\"stats\":${stats(tokens.incrementAndGet())}}\n\n"
                        output.write(frame.toByteArray())
                        output.flush()
                        Thread.sleep(50)
                    }
                } finally { streams.decrementAndGet() }
            } else {
                if (path == "/api/stats") statsReads.incrementAndGet()
                val body = when (path) {
                    "/api/health" -> "{\"ok\":true,\"role\":\"hub\"}"
                    "/api/stats" -> stats(tokenBase)
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
}
