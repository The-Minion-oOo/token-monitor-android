package io.github.theminionooo.tokenmonitor.data.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

internal data class UsageCurrency(val code: String = "USD", val rate: Double = 1.0)
internal val usageCurrencies = listOf("USD", "CNY", "TWD", "HKD", "INR", "EUR", "JPY", "KRW", "BRL")
internal fun usageCurrency(options: DisplayOptions): UsageCurrency {
    if (options.currencyCode == "USD" || options.currencyCode !in usageCurrencies) return UsageCurrency()
    val rate = (if (options.manualCurrencyRate) options.manualRates else options.exchangeRates)[options.currencyCode]
    return rate?.takeIf { it.isFinite() && it > 0 }?.let { UsageCurrency(options.currencyCode, it) } ?: UsageCurrency()
}
internal fun encodeRates(values: Map<String, Double>) = JsonObject(values.filter { it.key in usageCurrencies && it.value.isFinite() && it.value > 0 }.mapValues { JsonPrimitive(it.value) }).toString()
internal fun decodeRates(raw: String?): Map<String, Double> = runCatching {
    (Json.parseToJsonElement(raw.orEmpty()) as? JsonObject)?.mapNotNull { (code, value) ->
        (value as? JsonPrimitive)?.doubleOrNull?.takeIf { code in usageCurrencies && it.isFinite() && it > 0 }?.let { code to it }
    }?.toMap().orEmpty()
}.getOrDefault(emptyMap())
