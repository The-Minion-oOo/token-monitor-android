package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.storage.usageCurrencies
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class ExchangeRateUserAgentTest {
    @Test fun `both rate sources receive only the app version as user agent`() {
        val connections = mutableListOf<HttpURLConnection>()
        val rates = usageCurrencies.joinToString(",") { "\"${it.lowercase()}\":1" }
        ExchangeRateClient { source ->
            object : HttpURLConnection(URL(source)) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = if (connections.size == 1) 500 else 200
                override fun getInputStream() = """{"date":"2026-10-29","usd":{$rates}}""".byteInputStream()
            }.also(connections::add)
        }.use { assertNotNull(it.fetch()) }
        assertEquals(2, connections.size)
        connections.forEach { assertEquals("Token-Monitor-Android/${BuildConfig.VERSION_NAME}", it.getRequestProperty("User-Agent")) }
    }
}
