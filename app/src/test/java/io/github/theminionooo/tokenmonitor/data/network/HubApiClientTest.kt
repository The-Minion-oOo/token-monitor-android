package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HubApiClientTest {
    @Test fun `a broken revision exits the stream and reconnect starts from a new snapshot`() = runBlocking {
        val reads = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/stats/stream") { exchange ->
                assertEquals("4", exchange.requestHeaders.getFirst("x-token-monitor-stream"))
                val first = reads.incrementAndGet() == 1
                val revision = if (first) 1 else 20
                val payload = "event: snapshot\ndata: {\"revision\":$revision,\"stats\":{\"periods\":{\"today\":{\"totalTokens\":$revision}}}}\n\n" +
                    if (first) "event: delta\ndata: {\"baseRevision\":9,\"revision\":10,\"patch\":[]}\n\n" else ""
                val body = payload.toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
            val totals = mutableListOf<Long>()
            HubApiClient().use { client ->
                val collect: suspend (HubStreamEvent) -> Unit = { totals += HubProtocolParser.decodeStats(it.completeStats!!.toString()).periods.getValue("today").totalTokens }
                try { client.streamStats(connection, {}, collect); fail("Expected reconnect") }
                catch (_: io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolException) { }
                client.streamStats(connection, {}, collect)
            }
            assertEquals(listOf(1L, 20L), totals)
        } finally { server.stop(0) }
    }

    @Test fun `widget fetch authenticates after health reads three endpoints and counts payload bytes`() {
        val reads = mutableListOf<String>()
        val bodies = mapOf("/api/health" to """{"ok":true,"role":"hub"}""", "/api/stats" to "{}", "/api/history" to "{}")
        val server = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/") { exchange ->
                val path = exchange.requestURI.path
                reads += path
                assertEquals(if (path == "/api/health") null else "Bearer synthetic-secret", exchange.requestHeaders.getFirst("Authorization"))
                val body = bodies.getValue(path).toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            HubApiClient().use { api ->
                val wire = api.loadWidgetSnapshot(HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true), null)
                assertEquals(listOf("/api/health", "/api/stats", "/api/history"), reads)
                assertNull(wire.devices)
                assertNull(wire.subscriptions)
                assertEquals(bodies.values.sumOf { it.toByteArray().size }.toLong(), api.receivedBytes)
            }
        } finally { server.stop(0) }
    }
    // Synthetic servers use this machine's private address, with real LAN validation.
    private val testHost = java.net.NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }.first { it is java.net.Inet4Address && it.isSiteLocalAddress }.hostAddress

    @Test
    fun `expired ownership prevents the next endpoint in a snapshot read`() = runBlocking {
        val checks = AtomicInteger()
        val reads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/") { exchange ->
                val path = exchange.requestURI.path
                reads.add(path)
                val body = (if (path == "/api/health") """{"ok":true,"role":"hub"}""" else "{}").toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
            HubApiClient(beforeRequest = {
                if (checks.incrementAndGet() >= 2) throw CancellationException("Widget session expired")
            }).use { client ->
                try {
                    client.loadSnapshot(connection)
                    fail("Expected the expired lease to cancel before authenticated reads")
                } catch (_: CancellationException) {
                    assertEquals(listOf("/api/health"), reads.toList())
                    assertTrue(checks.get() >= 2)
                }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a valid snapshot above the old stream limit remains live`() = runBlocking {
        val sessions = (0 until 2400).joinToString(",") { index ->
            """"synthetic-$index":{"sessionId":"synthetic-$index","title":"Synthetic parser capacity sample $index","client":"codex","projectLabel":"Benchmark project","totalTokens":1000,"costUsd":0.01,"models":{"synthetic-model":1000},"messageCount":5,"startedAt":"2026-10-04T10:00:00Z","lastUsedAt":"2026-10-04T12:00:00Z"}"""
        }
        val stats = """{"periods":{"month":{"totalTokens":2400000,"sessions":{$sessions}}}}"""
        val payload = "event: snapshot\ndata: {\"stats\":$stats}\n\n"
        assertTrue(payload.length > 512 * 1024)
        assertTrue(payload.length < 2 * 1024 * 1024)
        val server = streamServer(payload)
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
            val events = mutableListOf<HubStreamEvent>()
            HubApiClient().use { client ->
                client.streamStats(connection, onOpen = {}, onEvent = { events.add(it) })
            }
            assertEquals(1, events.size)
            assertEquals("snapshot", events.single().type)
            val decoded = HubProtocolParser.decodeStats(events.single().completeStats!!.toString())
            assertEquals(2400, decoded.periods.getValue("month").sessions.size)
            assertEquals(2_400_000, decoded.periods.getValue("month").totalTokens)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `streaming still rejects an oversized event`() = runBlocking {
        val payload = "event: snapshot\ndata: {\"padding\":\"${"x".repeat(3 * 1024 * 1024)}\"}\n\n"
        val server = streamServer(payload)
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
            HubApiClient().use { client ->
                try {
                    client.streamStats(connection, onOpen = {}, onEvent = { fail("Oversized event must not be delivered") })
                    fail("Expected oversized SSE rejection")
                } catch (error: HubApiException) {
                    assertTrue(error.presentation.contains("too large"))
                }
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `an oversized optional endpoint is treated as omitted`() = runBlocking {
        val oversized = """{"padding":"${"x".repeat(3 * 1024 * 1024)}"}"""
        // Announced length and chunked streaming take different guards in the client.
        listOf(oversized.length.toLong(), 0L).forEach { announced ->
            val reads = java.util.Collections.synchronizedList(mutableListOf<String>())
            val server = detailServer(reads, oversized = setOf("/api/devices", "/api/subscriptions"), announced = announced)
            try {
                val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
                val snapshot = HubApiClient().use { it.loadSnapshot(connection) }
                assertNull(snapshot.devices)
                assertNull(snapshot.subscriptions)
                assertEquals("""{"daily":[]}""", snapshot.history)
                assertEquals("""{"updatedAt":"now"}""", snapshot.stats)
                assertEquals("/api/health", reads.first())
                assertEquals(setOf("/api/health", "/api/stats", "/api/devices", "/api/history", "/api/subscriptions"), reads.toSet())
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun `an oversized stats response still fails the snapshot`() = runBlocking {
        val server = detailServer(java.util.Collections.synchronizedList(mutableListOf()), oversized = setOf("/api/stats"), announced = 0L)
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "synthetic-secret", true)
            try {
                HubApiClient().use { it.loadSnapshot(connection) }
                fail("Expected an oversized stats response to be rejected")
            } catch (error: HubResponseTooLargeException) {
                assertTrue(error.presentation.contains("too large"))
            }
        } finally {
            server.stop(0)
        }
    }

    private fun detailServer(reads: MutableList<String>, oversized: Set<String>, announced: Long): HttpServer =
        HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/") { exchange ->
                val path = exchange.requestURI.path
                reads.add(path)
                try {
                    val body = when {
                        path in oversized -> """{"padding":"${"x".repeat(3 * 1024 * 1024)}"}"""
                        path == "/api/health" -> """{"ok":true,"role":"hub"}"""
                        path == "/api/stats" -> """{"updatedAt":"now"}"""
                        else -> """{"daily":[]}"""
                    }.toByteArray()
                    // The client closes an oversized response early; the server must not fail on that.
                    exchange.sendResponseHeaders(200, if (path in oversized) announced else body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                } catch (_: java.io.IOException) {
                    exchange.close()
                }
            }
            start()
        }

    private fun streamServer(payload: String): HttpServer = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
        createContext("/api/stats/stream") { exchange ->
            // A bounded reader deliberately closes an oversized response early.
            try {
                val body = payload.toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } catch (_: java.io.IOException) {
                exchange.close()
            }
        }
        start()
    }

    @Test
    fun `stats refresh reads only the changing aggregate`() {
        val authorization = AtomicReference<String?>()
        val statsReads = AtomicInteger()
        val detailReads = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/stats") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                statsReads.incrementAndGet()
                val body = """{"updatedAt":"now"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            listOf("/api/health", "/api/devices", "/api/history", "/api/subscriptions").forEach { path ->
                createContext(path) { exchange ->
                    detailReads.incrementAndGet()
                    exchange.sendResponseHeaders(500, -1)
                    exchange.close()
                }
            }
            start()
        }
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "private-secret", true)
            HubApiClient().use { client ->
                assertEquals("""{"updatedAt":"now"}""", client.getStats(connection))
            }
            assertEquals("Bearer private-secret", authorization.get())
            assertEquals(1, statsReads.get())
            assertEquals(0, detailReads.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `authenticated reads do not follow redirects`() = runBlocking {
        val receivedAuthorization = AtomicReference<String?>()
        val redirectTarget = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/capture") { exchange ->
                receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }
        val hub = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"hub"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                exchange.responseHeaders.add("Location", "http://$testHost:${redirectTarget.address.port}/capture")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }
        try {
            val connection = HubConnection("http://$testHost:${hub.address.port}", "private-secret", true)
            try {
                HubApiClient().loadSnapshot(connection)
                fail("Expected a redirect response to be rejected")
            } catch (error: HubApiException) {
                assertEquals(302, error.statusCode)
            }
            assertNull(receivedAuthorization.get())
        } finally {
            hub.stop(0)
            redirectTarget.stop(0)
        }
    }

    @Test
    fun `hub identity is checked before sending the secret`() = runBlocking {
        val receivedAuthorization = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress(testHost, 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"other"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }
        try {
            val connection = HubConnection("http://$testHost:${server.address.port}", "private-secret", true)
            try {
                HubApiClient().loadSnapshot(connection)
                fail("Expected a non-Hub identity to be rejected")
            } catch (_: HubApiException) {}
            assertNull(receivedAuthorization.get())
        } finally {
            server.stop(0)
        }
    }
}
