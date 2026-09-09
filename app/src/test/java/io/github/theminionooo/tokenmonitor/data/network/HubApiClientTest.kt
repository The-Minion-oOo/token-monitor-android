package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HubApiClientTest {
    @Test
    fun `stats refresh reads only the changing aggregate`() {
        val authorization = AtomicReference<String?>()
        val statsReads = AtomicInteger()
        val detailReads = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
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
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "private-secret", true)
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
    fun `authenticated reads do not follow redirects`() {
        val receivedAuthorization = AtomicReference<String?>()
        val redirectTarget = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/capture") { exchange ->
                receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.close()
            }
            start()
        }
        val hub = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/health") { exchange ->
                val body = """{"ok":true,"role":"hub"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/api/stats") { exchange ->
                exchange.responseHeaders.add("Location", "http://127.0.0.1:${redirectTarget.address.port}/capture")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            start()
        }
        try {
            val connection = HubConnection("http://127.0.0.1:${hub.address.port}", "private-secret", true)
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
    fun `hub identity is checked before sending the secret`() {
        val receivedAuthorization = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
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
            val connection = HubConnection("http://127.0.0.1:${server.address.port}", "private-secret", true)
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
