package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.historyInRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Null identifies unattributed usage; a reported tool or model may have any name. */
internal data class TrendSeries(val name: String?, val other: Boolean = false)

internal data class TrendDay(
    val date: LocalDate,
    val tokens: Long,
    val costUsd: Double,
    val segments: Map<TrendSeries, Long>,
    val inconsistentAttribution: Boolean,
    val unpricedTokens: Long = 0,
)

internal data class TrendPresentation(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<TrendDay>,
    val series: List<TrendSeries>,
) {
    val calendarDays: Long get() = ChronoUnit.DAYS.between(start, end) + 1
    val missingDays: Long get() = calendarDays - days.size
    val totalTokens: Long get() = days.sumOf { it.tokens }
    val unattributedTokens: Long get() = days.sumOf { it.segments[TrendSeries(null)] ?: 0 }
    fun dayOffset(date: LocalDate): Long = ChronoUnit.DAYS.between(start, date)
}

/** Keep gaps as dates, and never make a partial breakdown look like the daily total. */
internal fun prepareTrends(
    history: List<HistoryPoint>,
    byModel: Boolean,
    start: LocalDate?,
    end: LocalDate,
): TrendPresentation {
    val points = historyInRange(history, start ?: LocalDate.MIN, end)
    val first = start ?: points.firstOrNull()?.let { LocalDate.parse(it.label.take(10)) } ?: end
    require(!first.isAfter(end))
    val rawDays = points.map { point ->
        val total = point.tokens.coerceAtLeast(0)
        val entries = if (byModel) point.perModel else point.perClient
        var assigned = 0L
        val inconsistent = entries.values.any { value ->
            // Comparing before adding also prevents overflow in malformed attribution.
            if (value.tokens < 0 || value.tokens > total - assigned) true
            else { assigned += value.tokens; false }
        }
        val parts = if (inconsistent) mutableMapOf<TrendSeries, Long>() else entries
            .filterValues { it.tokens > 0 }.mapKeys { TrendSeries(it.key) }.mapValues { it.value.tokens }.toMutableMap()
        val remainder = if (inconsistent) total else total - assigned
        if (remainder > 0) parts[TrendSeries(null)] = remainder
        TrendDay(LocalDate.parse(point.label.take(10)), total, point.costUsd, parts, inconsistent, point.unpricedTokens)
    }
    val ranked = rawDays.flatMap { it.segments.entries }.filter { it.key.name != null }
        .groupBy { it.key }.entries.sortedByDescending { (_, entries) -> entries.sumOf { it.value } }.map { it.key }
    val visible = ranked.take(7).toSet()
    val other = TrendSeries(if (byModel) appText(R.string.ui_other_models_6b641, "Other models") else appText(R.string.ui_other_tools_e9c83, "Other tools"), other = true)
    val days = rawDays.map { day ->
        val parts = day.segments.filterKeys { it.name == null || it in visible }.toMutableMap()
        val otherTokens = day.segments.filterKeys { it.name != null && it !in visible }.values.sum()
        if (otherTokens > 0) parts[other] = otherTokens
        day.copy(segments = parts)
    }
    val series = visible.toList() + (if (ranked.size > visible.size) listOf(other) else emptyList()) +
        (if (days.any { TrendSeries(null) in it.segments }) listOf(TrendSeries(null)) else emptyList())
    return TrendPresentation(first, end, days, series)
}

internal data class TrendCandle(val first: LocalDate, val last: LocalDate, val open: Long, val close: Long, val high: Long, val low: Long)

internal fun trendCandleDays(calendarDays: Long): Int = when {
    calendarDays <= 10 -> 2
    calendarDays <= 30 -> 3
    calendarDays <= 90 -> 7
    else -> 14
}

/** A candle cannot span an absent day or silently treat it as a zero observation. */
internal fun trendCandles(trend: TrendPresentation): List<TrendCandle> {
    val bucketDays = trendCandleDays(trend.calendarDays)
    val result = mutableListOf<TrendCandle>()
    var run = mutableListOf<TrendDay>()
    fun finish() {
        if (run.isNotEmpty()) {
            result += TrendCandle(run.first().date, run.last().date, run.first().tokens, run.last().tokens,
                run.maxOf { it.tokens }, run.minOf { it.tokens })
            run = mutableListOf()
        }
    }
    trend.days.forEach { day ->
        val previous = run.lastOrNull()
        if (previous != null && (previous.date.plusDays(1) != day.date ||
                trend.dayOffset(previous.date) / bucketDays != trend.dayOffset(day.date) / bucketDays)) finish()
        run += day
    }
    finish()
    return result
}

internal fun trendChartDescription(trend: TrendPresentation, candles: Boolean): String = buildString {
    append(if (candles) appText(R.string.ui_usage_range_chart_fb5ca, "Usage range chart. ") else appText(R.string.ui_daily_usage_bar_chart_b0599, "Daily usage bar chart. "))
    append(appText(R.string.ui_1_s_tokens_across_2_s_recorded_days_3_s_to_4_s_77fab, "%1\$s tokens across %2\$s recorded days, %3\$s to %4\$s. ", formatTokens(trend.totalTokens), trend.days.size, trend.start, trend.end))
    if (trend.missingDays > 0) append(appText(R.string.ui_1_s_days_have_no_observation_and_are_left_blank_d778b, "%1\$s days have no observation and are left blank. ", trend.missingDays))
    trend.days.maxByOrNull { it.tokens }?.let { append(appText(R.string.ui_highest_day_1_s_2_s_tokens_3cad8, "Highest day: %1\$s, %2\$s tokens. ", it.date, formatTokens(it.tokens))) }
    if (candles) append(appText(R.string.ui_each_candle_covers_up_to_1_s_consecutive_recorded_da_a073f, "Each candle covers up to %1\$s consecutive recorded days: first, last, highest and lowest daily totals.", trendCandleDays(trend.calendarDays)))
    else if (trend.unattributedTokens > 0) append(appText(R.string.ui_1_s_tokens_have_no_reliable_breakdown_and_are_shown_b140c, "%1\$s tokens have no reliable breakdown and are shown as unattributed.", formatTokens(trend.unattributedTokens)))
}
