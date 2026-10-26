package io.github.theminionooo.tokenmonitor.data.storage

import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun encodeDisplayList(values: Collection<String>): String = JsonArray(values.distinct().map(::JsonPrimitive)).toString()
internal fun decodeDisplayList(raw: String?): List<String> = runCatching {
    (Json.parseToJsonElement(raw.orEmpty()) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.distinct().orEmpty()
}.getOrDefault(emptyList())

internal fun limitItemKey(provider: String, window: LimitWindow): String = JsonArray(listOf(provider, window.kind, window.label).map(::JsonPrimitive)).toString()
internal fun providerDetailKey(provider: String, detail: String): String = JsonArray(listOf(provider, detail).map(::JsonPrimitive)).toString()
internal fun visibleLimitAccounts(accounts: List<LimitAccount>, options: DisplayOptions): List<LimitAccount> = accounts
    .filterNot { it.provider in options.hiddenProviders }
    .map { account -> account.copy(windows = account.windows.filterNot { limitItemKey(account.provider, it) in options.hiddenLimitItems }) }

internal fun orderedTools(tokens: Map<String, Long>, costs: Map<String, Double>, options: DisplayOptions): List<Map.Entry<String, Long>> {
    val pins = options.pinnedTools.withIndex().associate { it.value to it.index }
    val order = options.toolOrder.withIndex().associate { it.value to it.index }
    return tokens.entries.filterNot { it.key in options.hiddenTools }.sortedWith(
        compareBy<Map.Entry<String, Long>> { if (it.key in pins) 0 else 1 }
            .thenBy { pins[it.key] ?: Int.MAX_VALUE }
            .thenBy { order[it.key] ?: Int.MAX_VALUE }
            .thenByDescending { if (options.rankingMetric == RankingMetric.Cost) costs[it.key] ?: 0.0 else it.value.toDouble() }
            .thenBy { it.key },
    )
}

internal fun moveDisplayItem(values: List<String>, id: String, offset: Int): List<String> {
    val list = values.distinct().toMutableList()
    val index = list.indexOf(id)
    if (index < 0) return list
    val target = (index + offset).coerceIn(0, list.lastIndex)
    list.removeAt(index)
    list.add(target, id)
    return list
}
