package io.github.theminionooo.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.network.*
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HubFailoverDeadlineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val address get() = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }

    @Test fun stalledPrimaryReproducesTheNormalConnectionWaitBeforeFallback() = runBlocking {
        exerciseFailover("normal", HubHealthConnectBudget.Normal)
    }

    @Test fun stalledPrimaryYieldsToTheHealthyFallbackWithTheShortBudget() = runBlocking {
        exerciseFailover("short", HubHealthConnectBudget.FastHomeFallback)
    }

    @Test fun anEstablishedHealthConnectionKeepsItsLongerHeaderReadBudget() = runBlocking {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub(address, healthHeaderDelayMs = 2300).use { server ->
            val samples = CopyOnWriteArrayList<HubTimingSample>()
            HubApiClient(HubTimingRecorder(true, sink = samples::add)).use { client ->
                val snapshot = client.loadSnapshot(HubConnection(server.url, SECRET, true), HubHealthConnectBudget.FastHomeFallback)
                assertTrue(snapshot.stats.contains("1234"))
                assertTrue(samples.single { it.stage == HubTimingStage.HEADERS && it.endpoint == HubTimingEndpoint.HEALTH }.durationUs >= 2_200_000)
                assertEquals(5, server.paths.size)
                assertTrue(server.authCorrect)
                writeSamples("slow-connected-health", samples)
            }
        }
    }

    @Test fun authenticatedStatsBodyReadsAreNotShortenedByTheHealthBudget() = runBlocking {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        FixtureHub(address, statsBodyDelayMs = 2300).use { server ->
            val samples = CopyOnWriteArrayList<HubTimingSample>()
            HubApiClient(HubTimingRecorder(true, sink = samples::add)).use { client ->
                val snapshot = client.loadSnapshot(HubConnection(server.url, SECRET, true), HubHealthConnectBudget.FastHomeFallback)
                assertTrue(snapshot.stats.contains("1234"))
                assertTrue(samples.single { it.stage == HubTimingStage.BODY && it.endpoint == HubTimingEndpoint.STATS }.durationUs >= 2_200_000)
                assertEquals(5, server.paths.size)
                assertTrue(server.authCorrect)
                writeSamples("slow-stats-body", samples)
            }
        }
    }

    private suspend fun exerciseFailover(name: String, budget: HubHealthConnectBudget) {
        check(context.packageName.endsWith(".preview"))
        grantFixtureNetworkAccess()
        val host = address
        PendingConnect(host).use { primary ->
            FixtureHub(host).use { fallback ->
                val samples = CopyOnWriteArrayList<HubTimingSample>()
                val timing = HubTimingRecorder(true, sink = samples::add)
                val connection = HubConnection(primary.url, SECRET, true, fallbackUrl = fallback.url)
                HubApiClient(timing).use { client ->
                    val (url, snapshot) = timing.measureSuspending(HubTimingStage.SNAPSHOT) {
                        EndpointFailover.run(listOf(primary.url, fallback.url)) {
                            client.loadSnapshot(connection.copy(baseUrl = it), if (it == primary.url) budget else HubHealthConnectBudget.Normal)
                        }
                    }
                    assertEquals(fallback.url, url)
                    assertTrue(snapshot.stats.contains("1234"))
                    val failed = samples.first { it.stage == HubTimingStage.REQUEST }
                    assertEquals(HubTimingEndpoint.HEALTH, failed.endpoint)
                    assertEquals(0, failed.status)
                    if (budget == HubHealthConnectBudget.Normal) {
                        assertTrue("A real stalled TCP connection must consume the normal budget", failed.durationUs >= 9_000_000)
                    } else {
                        assertTrue("The shorter dial must still reach a real connection timeout", failed.durationUs >= 1_500_000)
                        assertTrue("The healthy fallback must not wait for the normal ten-second dial", failed.durationUs < 8_000_000)
                    }
                    assertEquals(5, fallback.paths.size)
                    assertTrue(fallback.authCorrect)
                    writeSamples(name, samples)
                }
            }
        }
    }

    private fun writeSamples(name: String, samples: List<HubTimingSample>) {
        val folder = context.filesDir.resolve("performance").also { it.mkdirs() }
        folder.resolve("failover-deadline-$name.log").writeText(samples.joinToString("\n") { it.line() })
    }

    /** A full accept queue drops new SYNs locally: no root, firewall edits or external address. */
    private class PendingConnect(address: InetAddress) : AutoCloseable {
        private val server = ServerSocket(0, 1, address)
        private val held = mutableListOf<Socket>()
        val url = "http://${address.hostAddress}:${server.localPort}"

        init {
            var full = false
            try {
                for (attempt in 0 until 32) {
                    val socket = Socket()
                    try {
                        socket.connect(InetSocketAddress(address, server.localPort), 200)
                        held.add(socket)
                    } catch (_: SocketTimeoutException) {
                        socket.close()
                        full = true
                        break
                    } catch (error: Exception) { socket.close(); throw error }
                }
                check(full) { "Fixture could not establish a stalled local TCP connect" }
            } catch (error: Exception) { close(); throw error }
        }

        override fun close() { server.close(); held.forEach { runCatching { it.close() } } }
    }

    private class FixtureHub(address: InetAddress, private val healthHeaderDelayMs: Long = 0,
                             private val statsBodyDelayMs: Long = 0) : AutoCloseable {
        private val server = ServerSocket(0, 16, address)
        val url = "http://${address.hostAddress}:${server.localPort}"
        val paths = CopyOnWriteArrayList<String>()
        @Volatile var authCorrect = true
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val acceptor = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                sockets.add(socket)
                thread(isDaemon = true) {
                    try { socket.use { respond(it) } } catch (_: Exception) { /* Cleanup closes test sockets. */ }
                    finally { sockets.remove(socket) }
                }
            }
        }

        private fun respond(socket: Socket) {
            socket.soTimeout = 5000
            val input = socket.getInputStream().bufferedReader()
            val path = input.readLine().split(' ')[1]
            var auth: String? = null
            while (true) {
                val header = input.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Authorization:", true)) auth = header.substringAfter(':').trim()
            }
            if (if (path == "/api/health") auth != null else auth != "Bearer $SECRET") authCorrect = false
            paths.add(path)
            if (path == "/api/health") Thread.sleep(healthHeaderDelayMs)
            val body = when (path) {
                "/api/health" -> """{"ok":true,"role":"hub"}"""
                "/api/stats" -> """{"periods":{"today":{"totalTokens":1234}}}"""
                else -> "{}"
            }.toByteArray()
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.flush()
            if (path == "/api/stats") Thread.sleep(statsBodyDelayMs)
            output.write(body)
            output.flush()
        }

        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1000)
        }
    }

    companion object { private const val SECRET = "synthetic-deadline-secret" }
}
