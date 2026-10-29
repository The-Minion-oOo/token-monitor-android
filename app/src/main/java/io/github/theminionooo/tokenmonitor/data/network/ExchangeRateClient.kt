package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.data.storage.usageCurrencies
import io.github.theminionooo.tokenmonitor.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.*

internal data class ExchangeRates(val rates: Map<String, Double>, val date: String)

/** Same public rate sources as desktop. No Hub URL, secret or usage enters this client. */
internal class ExchangeRateClient(
    private val openConnection: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) : AutoCloseable {
    private val active = AtomicReference<HttpURLConnection?>()
    private val generation = java.util.concurrent.atomic.AtomicInteger()
    fun fetch(): ExchangeRates {
        val expectedGeneration = generation.get()
        var failure: Exception? = null
        for (source in listOf("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json", "https://latest.currency-api.pages.dev/v1/currencies/usd.json")) {
            val connection = openConnection(source)
            active.set(connection)
            try {
                if (generation.get() != expectedGeneration) throw kotlinx.coroutines.CancellationException()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
                check(connection.responseCode == 200)
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(bytes.size() + count <= 65_536)
                        bytes.write(buffer, 0, count)
                    }
                }
                return parseExchangeRates(bytes.toString("UTF-8")) ?: error("Invalid exchange rates")
            } catch (error: Exception) {
                if (generation.get() != expectedGeneration) throw kotlinx.coroutines.CancellationException()
                failure = error
            }
            finally { active.compareAndSet(connection, null); connection.disconnect() }
        }
        throw failure ?: IllegalStateException("Exchange rates unavailable")
    }
    override fun close() { generation.incrementAndGet(); active.getAndSet(null)?.disconnect() }
}

internal fun parseExchangeRates(raw: String): ExchangeRates? = runCatching {
    val value = Json.parseToJsonElement(raw).jsonObject
    val rates = value["usd"]?.jsonObject ?: return null
    val date = value["date"]?.jsonPrimitive?.contentOrNull ?: return null
    LocalDate.parse(date)
    val parsed = usageCurrencies.associateWith { code ->
        if (code == "USD") 1.0 else rates[code.lowercase()]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0 } ?: return null
    }
    ExchangeRates(parsed, date)
}.getOrNull()
