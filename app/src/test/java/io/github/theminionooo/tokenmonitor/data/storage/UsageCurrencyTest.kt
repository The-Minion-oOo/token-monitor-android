package io.github.theminionooo.tokenmonitor.data.storage

import io.github.theminionooo.tokenmonitor.data.network.parseExchangeRates
import io.github.theminionooo.tokenmonitor.ui.formatSubscriptionAmount
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import org.junit.Assert.*
import org.junit.Test

class UsageCurrencyTest {
    @org.junit.Test fun `failed automatic attempts back off but a day later can refresh`() {
        val options = DisplayOptions(currencyCode = "INR", exchangeRateAttemptedAt = 1000)
        org.junit.Assert.assertFalse(shouldRefreshRates(options, 2000))
        org.junit.Assert.assertTrue(shouldRefreshRates(options, 86_401_000))
        org.junit.Assert.assertFalse(shouldRefreshRates(options.copy(manualCurrencyRate = true), 86_401_000))
        org.junit.Assert.assertFalse(shouldRefreshRates(DisplayOptions(), 86_401_000))
    }
    @Test fun `newly supported currencies can refresh an older saved table once`() {
        val options = DisplayOptions(currencyCode = "CAD", exchangeRates = mapOf("EUR" to 0.9),
            exchangeRateFetchedAt = 1000, exchangeRateAttemptedAt = 900)
        assertTrue(shouldRefreshRates(options, 2000))
        assertFalse(shouldRefreshRates(options.copy(exchangeRateAttemptedAt = 2000), 3000))
        assertFalse(shouldRefreshRates(options.copy(currencyCode = "EUR"), 2000))
    }
    @Test fun `conversion changes displayed usage only and native payment currency stays intact`() {
        val currency = usageCurrency(DisplayOptions(currencyCode = "INR", manualCurrencyRate = true, manualRates = mapOf("INR" to 83.0)))
        assertEquals("INR", currency.code)
        assertTrue(formatUsageCost(2.0, currency = currency).contains("166"))
        assertEquals("$2.00", formatSubscriptionAmount(200, "USD"))
        assertEquals("CAD 12.34", formatSubscriptionAmount(1234, "CAD"))
        assertEquals("KRW 12,345", formatSubscriptionAmount(1_234_500, "KRW"))
        assertEquals("JPY 12,345", formatSubscriptionAmount(1_234_500, "JPY"))
        assertEquals("₩1,380", formatUsageCost(1.0, currency = UsageCurrency("KRW", 1380.0)))
        assertTrue(formatUsageCost(2.0, 100, currency = currency).contains("unpriced"))
    }
    @Test fun `missing invalid and partial rates do not masquerade as converted costs`() {
        assertEquals("USD", usageCurrency(DisplayOptions(currencyCode = "CNY")).code)
        assertEquals("USD", usageCurrency(DisplayOptions(currencyCode = "CNY", manualCurrencyRate = true, manualRates = mapOf("CNY" to Double.NaN))).code)
        assertNull(parseExchangeRates("""{"date":"2026-10-26","usd":{"cny":7}}"""))
        val rates = usageCurrencies.filterNot { it == "USD" }.joinToString(",") { "\"${it.lowercase()}\":2" }
        val parsed = parseExchangeRates("""{"date":"2026-10-26","usd":{$rates}}""")!!.rates
        assertEquals(14, parsed.size)
        val aud = usageCurrency(DisplayOptions(currencyCode = "AUD", exchangeRates = parsed))
        assertEquals(UsageCurrency("AUD", 2.0), aud)
        assertTrue(decodeRates(encodeRates(mapOf("USD" to 1.0, "CNY" to -1.0))).keys == setOf("USD"))
    }
}
