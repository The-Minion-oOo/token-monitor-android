package io.github.theminionooo.tokenmonitor.domain

import java.util.Locale

internal enum class ModelAliasGrouping { Off, Duplicates, Prefix }

internal data class ModelAliasSettings(
    val revision: Long,
    val aliases: Map<String, String>,
    val grouping: ModelAliasGrouping,
) {
    fun resolver(snapshot: HubSnapshot): ModelNames {
        if (grouping == ModelAliasGrouping.Off) return ModelNames(aliases)
        val ids = LinkedHashSet<String>()
        fun add(values: Iterable<String>) {
            for (value in values) {
                if (ids.size == 16_384) return
                val id = modelText(value)
                if (id.length in 1..256) ids.add(id)
            }
        }
        fun collect(period: UsagePeriod) {
            add(period.models.keys)
            add(period.modelCosts.keys)
            add(period.modelCacheReads.keys)
            add(period.modelCacheWrites.keys)
            add(period.modelOutputs.keys)
            add(period.modelUnclassifiedTokens.keys)
            add(period.modelUnpricedTokens.keys)
            add(period.modelThroughput.keys)
            period.clientModels.values.forEach { add(it.keys) }
            period.clientModelCosts.values.forEach { add(it.keys) }
            period.clientModelUnpricedTokens.values.forEach { add(it.keys) }
            period.projects.forEach { add(it.models.keys) }
            period.sessions.forEach {
                add(it.modelNames)
                add(it.modelTokens.keys)
                it.latestModel?.let { model -> add(listOf(model)) }
            }
        }
        snapshot.stats.periods.values.forEach(::collect)
        snapshot.stats.devices.forEach { device -> device.periods.values.forEach(::collect) }
        snapshot.history.daily.forEach { add(it.perModel.keys) }
        snapshot.history.monthly.forEach { add(it.perModel.keys) }
        return resolver(ids)
    }

    fun resolver(ids: Iterable<String>): ModelNames {
        if (grouping == ModelAliasGrouping.Off) return ModelNames(aliases)
        val groups = ids.asSequence().map(::modelText).filter { it.length in 1..256 }
            .distinct().take(16_384).groupBy { modelMatchKey(modelLeaf(it)) }
        val automatic = LinkedHashMap<String, String>()
        var automaticCount = 0
        for ((identity, group) in groups) {
            if (identity.isEmpty() || (grouping == ModelAliasGrouping.Duplicates && group.size < 2)) continue
            // Match the desktop's preference for a bare, lowercase, hyphenated leaf.
            val canonical = modelLeaf(group.minWith(compareBy(
                { it != modelLeaf(it) },
                { modelLeaf(it) != modelLeaf(it).lowercase(Locale.ROOT) },
                { modelSeparators.containsMatchIn(modelLeaf(it)) },
                { modelLeaf(it).length },
                { modelLeaf(it).lowercase(Locale.ROOT) },
                { modelLeaf(it) },
            )))
            for (id in group) {
                if (id == canonical) continue
                automatic[modelMatchKey(id)] = canonical
                automaticCount++
                if (automaticCount == 4096) return ModelNames(aliases, automatic)
            }
        }
        return ModelNames(aliases, automatic)
    }
}

private val modelWhitespace = Regex("[\\s\\p{Z}\\uFEFF]")
private val modelSeparators = Regex("[._\\s\\p{Z}\\uFEFF]+")
internal fun modelText(value: String): String = value.trim { modelWhitespace.matches(it.toString()) }
private val modelHyphens = Regex("-+")
internal fun modelMatchKey(value: String): String =
    modelText(value).lowercase(Locale.ROOT).replace(modelSeparators, "-").replace(modelHyphens, "-").trim('-')
private fun modelLeaf(value: String): String = value.split('/').lastOrNull { it.isNotEmpty() } ?: value

internal data class ModelNames(
    private val manual: Map<String, String> = emptyMap(),
    private val automatic: Map<String, String> = emptyMap(),
) {
    private val sources = (automatic.keys + manual.keys).associateBy(::name)

    /** Preserve a source model for vendor marks while labels and amounts are folded. */
    fun source(label: String): String = sources[label] ?: label

    fun name(source: String): String {
        if (manual.isEmpty() && automatic.isEmpty()) return source
        val key = modelMatchKey(source)
        return manual[key] ?: automatic[key]?.let { manual[modelMatchKey(it)] ?: it } ?: source
    }

    fun <T : Any> fold(values: Map<String, T>, combine: (T, T) -> T): Map<String, T> {
        if (values.keys.none { name(it) != it }) return values
        val folded = LinkedHashMap<String, T>()
        for ((source, value) in values) folded.merge(name(source), value, combine)
        return folded
    }

    fun history(points: List<HistoryPoint>): List<HistoryPoint> {
        if (manual.isEmpty() && automatic.isEmpty()) return points
        return points.map { point ->
            val models = fold(point.perModel) { first, second ->
                HistoryAttribution(
                    tokens = first.tokens + second.tokens,
                    costUsd = first.costUsd + second.costUsd,
                    cacheReadTokens = first.cacheReadTokens + second.cacheReadTokens,
                    cacheWriteTokens = first.cacheWriteTokens + second.cacheWriteTokens,
                    outputTokens = first.outputTokens + second.outputTokens,
                    unclassifiedTokens = first.unclassifiedTokens + second.unclassifiedTokens,
                    unpricedTokens = first.unpricedTokens + second.unpricedTokens,
                )
            }
            if (models === point.perModel) point else point.copy(perModel = models)
        }
    }

    fun usage(period: UsagePeriod): UsagePeriod {
        if (manual.isEmpty() && automatic.isEmpty()) return period
        return period.copy(
            models = fold(period.models, Long::plus),
            modelCosts = fold(period.modelCosts, Double::plus),
            modelUnpricedTokens = fold(period.modelUnpricedTokens, Long::plus),
            modelCacheReads = fold(period.modelCacheReads, Long::plus),
            modelCacheWrites = fold(period.modelCacheWrites, Long::plus),
            modelOutputs = fold(period.modelOutputs, Long::plus),
            modelUnclassifiedTokens = fold(period.modelUnclassifiedTokens, Long::plus),
            modelThroughput = fold(period.modelThroughput) { first, second ->
                ThroughputCounters(first.timedTokens + second.timedTokens,
                    first.timedOutputTokens + second.timedOutputTokens, first.timedDurationMs + second.timedDurationMs)
            },
        )
    }
}
