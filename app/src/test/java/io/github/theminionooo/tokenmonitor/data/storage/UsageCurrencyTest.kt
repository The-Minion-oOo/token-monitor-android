package io.github.theminionooo.tokenmonitor.data.storage

import io.github.theminionooo.tokenmonitor.data.network.parseExchangeRates
import io.github.theminionooo.tokenmonitor.ui.formatSubscriptionAmount
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import org.junit.Assert.*
import org.junit.Test

class UsageCurrencyTest {
    @Test fun `conversion changes displayed usage only and native payment currency stays intact`() {
        val currency = usageCurrency(DisplayOptions(currencyCode = "INR", manualCurrencyRate = true, manualRates = mapOf("INR" to 83.0)))
        assertEquals("INR", currency.code)
        assertTrue(formatUsageCost(2.0, currency = currency).contains("166"))
        assertEquals("$2.00", formatSubscriptionAmount(200, "USD"))
        assertTrue(formatUsageCost(2.0, 100, currency = currency).contains("unpriced"))
    }
    @Test fun `missing invalid and partial rates do not masquerade as converted costs`() {
        assertEquals("USD", usageCurrency(DisplayOptions(currencyCode = "CNY")).code)
        assertEquals("USD", usageCurrency(DisplayOptions(currencyCode = "CNY", manualCurrencyRate = true, manualRates = mapOf("CNY" to Double.NaN))).code)
        assertNull(parseExchangeRates("""{"date":"2026-10-26","usd":{"cny":7}}"""))
        val rates = usageCurrencies.filterNot { it == "USD" }.joinToString(",") { "\"${it.lowercase()}\":2" }
        assertEquals(9, parseExchangeRates("""{"date":"2026-10-26","usd":{$rates}}""")!!.rates.size)
        assertTrue(decodeRates(encodeRates(mapOf("USD" to 1.0, "CNY" to -1.0))).keys == setOf("USD"))
    }
}
