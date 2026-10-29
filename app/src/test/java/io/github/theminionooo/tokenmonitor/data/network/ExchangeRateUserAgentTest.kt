package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class ExchangeRateUserAgentTest {
    @Test fun `both rate sources receive only the app version as user agent`() {
        val connections = mutableListOf<HttpURLConnection>()
        ExchangeRateClient { source ->
            object : HttpURLConnection(URL(source)) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = if (connections.size == 1) 500 else 200
                override fun getInputStream() = """{"date":"2026-10-29","usd":{"cny":7,"twd":32,"hkd":8,"inr":84,"eur":1,"jpy":150,"krw":1300,"brl":5}}""".byteInputStream()
            }.also(connections::add)
        }.use { assertNotNull(it.fetch()) }
        assertEquals(2, connections.size)
        connections.forEach { assertEquals("Token-Monitor-Android/${BuildConfig.VERSION_NAME}", it.getRequestProperty("User-Agent")) }
    }
}
