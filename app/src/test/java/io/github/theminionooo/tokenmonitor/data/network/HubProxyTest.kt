package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.IOException
import java.net.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HubProxyTest {
    @Test fun `HTTP reads streams and discovery probes bypass a configured proxy`() {
        val host = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().first { it.isSiteLocalAddress }.hostAddress
        val proxyLookups = AtomicInteger()
        val original = ProxySelector.getDefault()
        val server = HttpServer.create(InetSocketAddress(host, 0), 0).apply {
            createContext("/api/") { exchange ->
                val body = when (exchange.requestURI.path) {
                    "/api/health" -> """{"ok":true,"role":"hub"}"""
                    "/api/stats/stream" -> "event: snapshot\ndata: {\"stats\":{}}\n\n"
                    else -> "{}"
                }.toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> {
                    proxyLookups.incrementAndGet()
                    return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))
                }
                override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) = Unit
            })
            val base = "http://$host:${server.address.port}"
            HubApiClient().use { client ->
                assertEquals("{}", client.getStats(HubConnection(base, "synthetic-secret", true)))
                assertTrue(client.probeHub(base, 1000))
                var events = 0
                runBlocking { client.streamStats(HubConnection(base, "synthetic-secret", true), {}, { events++ }) }
                assertEquals(1, events)
            }
            assertEquals("No cleartext Hub request may consult the system proxy", 0, proxyLookups.get())
        } finally { ProxySelector.setDefault(original); server.stop(0) }
    }

    @Test fun `HTTPS opening retains the system proxy selection path`() {
        var systemOpened = false
        val handler = object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection {
                systemOpened = true
                return object : HttpURLConnection(url) {
                    override fun connect() = Unit
                    override fun disconnect() = Unit
                    override fun usingProxy() = true
                }
            }
            override fun openConnection(url: URL, proxy: Proxy): URLConnection = error("HTTPS proxy support must remain enabled")
        }
        openHubConnection(URL(null, "https://hub.example.com", handler)).disconnect()
        assertTrue(systemOpened)
    }
}
