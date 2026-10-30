package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HubSnapshotConcurrencyTest {
    @Test fun `snapshot overlaps two reads after anonymous identity and counts each body`() = runBlocking {
        Fixture().use { fixture ->
            val samples = CopyOnWriteArrayList<HubTimingSample>()
            val checks = AtomicInteger()
            HubApiClient(HubTimingRecorder(true, sink = samples::add)) { checks.incrementAndGet(); Unit }.use { client ->
                val snapshot = client.loadSnapshot(fixture.connection)
                assertEquals(fixture.bodies.getValue("/api/stats"), snapshot.stats)
                assertEquals(fixture.bodies.getValue("/api/devices"), snapshot.devices)
                assertEquals(fixture.bodies.getValue("/api/history"), snapshot.history)
                assertEquals(fixture.bodies.getValue("/api/subscriptions"), snapshot.subscriptions)
                assertEquals("/api/health", fixture.reads.first())
                assertTrue(fixture.anonymousHealth)
                assertTrue(fixture.authenticatedReads)
                assertEquals(2, fixture.peakWork.get())
                assertEquals(5, checks.get())
                assertEquals(fixture.bodies.values.sumOf { it.toByteArray().size.toLong() }, client.receivedBytes)
                val requests = samples.filter { it.stage == HubTimingStage.REQUEST }
                assertEquals(5, requests.size)
                fixture.bodies.forEach { (path, body) ->
                    assertEquals(body.toByteArray().size.toLong(), requests.single {
                        it.endpoint == HubTimingEndpoint.fromPath(path)
                    }.bytes)
                }
            }
        }
    }

    @Test fun `optional unauthorized response ends the batch without closing its client`() = runBlocking {
        Fixture(deviceStatus = 401, statsDelayMs = 1500).use { fixture ->
            HubApiClient().use { client ->
                try {
                    client.loadSnapshot(fixture.connection)
                    fail("The rejected secret must fail the whole snapshot")
                } catch (error: HubApiException) {
                    assertEquals(401, error.statusCode)
                }
                assertNotNull(client.getSubscriptions(fixture.connection))
            }
        }
    }

    @Test fun `a failed parallel batch can recover on the next permitted route`() = runBlocking {
        Fixture(deviceStatus = 500, statsDelayMs = 1500).use { unavailable ->
            Fixture().use { healthy ->
                HubApiClient().use { client ->
                    val (url, snapshot) = EndpointFailover.run(listOf(unavailable.connection.baseUrl, healthy.connection.baseUrl)) {
                        client.loadSnapshot(healthy.connection.copy(baseUrl = it))
                    }
                    assertEquals(healthy.connection.baseUrl, url)
                    assertEquals(healthy.bodies.getValue("/api/stats"), snapshot.stats)
                    assertEquals(5, healthy.reads.size)
                }
            }
        }
    }

    @Test fun `closing the client during parallel reads prevents queued requests`() = runBlocking {
        Fixture(statsDelayMs = 1500, delayMs = 1500).use { fixture ->
            HubApiClient().use { client ->
                supervisorScope {
                    val snapshot = async(Dispatchers.IO) { client.loadSnapshot(fixture.connection) }
                    withTimeout(6000) { while (fixture.reads.size < 3) delay(10) }
                    client.close()
                    try {
                        snapshot.await()
                        fail("Closed reads must not return a snapshot")
                    } catch (_: Exception) { /* Closing the socket ends a blocking read. */ }
                    assertEquals(3, fixture.reads.size)
                }
            }
        }
    }

    private class Fixture(
        private val deviceStatus: Int = 200,
        private val statsDelayMs: Long = 100,
        private val delayMs: Long = 100,
    ) : AutoCloseable {
        val bodies = linkedMapOf(
            "/api/health" to """{"ok":true,"role":"hub"}""",
            "/api/stats" to """{"periods":{"today":{"totalTokens":1234}}}""",
            "/api/devices" to """{"devices":[]}""",
            "/api/history" to """{"daily":[]}""",
            "/api/subscriptions" to """{"subscriptions":[]}""",
        )
        val reads = CopyOnWriteArrayList<String>()
        private val activeWork = AtomicInteger()
        val peakWork = AtomicInteger()
        @Volatile var anonymousHealth = false
        @Volatile var authenticatedReads = true
        private val executor = Executors.newFixedThreadPool(5) { task -> Thread(task).apply { isDaemon = true } }
        private val host = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        private val server = HttpServer.create(InetSocketAddress(host, 0), 0).apply {
            this.executor = this@Fixture.executor
            createContext("/") { exchange ->
                try {
                    val path = exchange.requestURI.path
                    val auth = exchange.requestHeaders.getFirst("Authorization")
                    if (path == "/api/health") anonymousHealth = auth == null
                    else if (auth != "Bearer synthetic-secret") authenticatedReads = false
                    reads.add(path)
                    val active = activeWork.incrementAndGet()
                    peakWork.updateAndGet { maxOf(it, active) }
                    try {
                        Thread.sleep(if (path == "/api/stats") statsDelayMs else delayMs)
                    } finally { activeWork.decrementAndGet() }
                    val body = bodies.getValue(path).toByteArray()
                    exchange.sendResponseHeaders(if (path == "/api/devices") deviceStatus else 200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                } catch (_: Exception) { /* Cancellation deliberately closes pending sockets. */ }
                finally { exchange.close() }
            }
            start()
        }
        val connection = HubConnection("http://$host:${server.address.port}", "synthetic-secret", true)
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
}
