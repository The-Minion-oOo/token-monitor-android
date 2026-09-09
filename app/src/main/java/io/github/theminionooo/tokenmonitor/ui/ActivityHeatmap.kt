package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The desktop Home view uses a rolling, Sunday-first contribution grid. Keep the
 * geometry and intensity thresholds here independent from Compose so old or
 * partially populated Hub histories remain safe to render and easy to verify.
 */
internal data class ActivityHeatmap(
    val cells: List<ActivityHeatmapCell>,
    val monthLabels: List<ActivityHeatmapMonthLabel>,
    val weeks: Int,
    val activeDays: Int,
)

internal data class ActivityHeatmapCell(
    val date: LocalDate,
    val column: Int,
    val row: Int,
    val intensity: Int,
)

internal data class ActivityHeatmapMonthLabel(
    val column: Int,
    val date: LocalDate,
)

internal enum class ActivityMetric { Automatic, Tokens, Cost }

/**
 * Matches the desktop renderer's default cost-based ramp for the rolling
 * twelve-month contribution grid. If an older Hub omits costs, token values
 * provide the equivalent useful fallback instead of producing an empty ramp.
 */
internal fun buildActivityHeatmap(
    points: List<HistoryPoint>,
    endDate: LocalDate = LocalDate.now(),
    metric: ActivityMetric = ActivityMetric.Automatic,
): ActivityHeatmap {
    val daily = buildMap {
        points.forEach { point ->
            parseLocalDate(point.label)?.let { date ->
                // The desktop's date map is last-write-wins for duplicate records.
                put(
                    date,
                    ActivityValue(
                        tokens = point.tokens.coerceAtLeast(0),
                        costUsd = point.costUsd.coerceAtLeast(0.0),
                    ),
                )
            }
        }
    }
    val useCosts = when (metric) {
        ActivityMetric.Automatic -> daily.values.any { it.costUsd > 0.0 }
        ActivityMetric.Tokens -> false
        ActivityMetric.Cost -> true
    }
    val maximum = daily.values.maxOfOrNull { value -> if (useCosts) value.costUsd else value.tokens.toDouble() } ?: 0.0

    val firstDay = endDate.withDayOfMonth(1).minusMonths(11)
    val gridStart = firstDay.minusDays((firstDay.dayOfWeek.value % 7).toLong())
    val cells = buildList {
        var date = gridStart
        while (!date.isAfter(endDate)) {
            val offset = java.time.temporal.ChronoUnit.DAYS.between(gridStart, date).toInt()
            val value = daily[date]
            val metric = if (useCosts) value?.costUsd ?: 0.0 else value?.tokens?.toDouble() ?: 0.0
            add(
                ActivityHeatmapCell(
                    date = date,
                    column = offset / 7,
                    row = offset % 7,
                    intensity = heatmapIntensity(metric, maximum),
                ),
            )
            date = date.plusDays(1)
        }
    }
    val monthLabels = cells.filter { it.date.dayOfMonth == 1 }.map {
        ActivityHeatmapMonthLabel(column = it.column, date = it.date)
    }
    val activeDays = cells.count { daily[it.date]?.tokens ?: 0L > 0L }
    return ActivityHeatmap(
        cells = cells,
        monthLabels = monthLabels,
        weeks = cells.lastOrNull()?.column?.plus(1) ?: 0,
        activeDays = activeDays,
    )
}

private data class ActivityValue(val tokens: Long, val costUsd: Double)

private fun parseLocalDate(value: String): LocalDate? = runCatching {
    LocalDate.parse(value.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
}.getOrNull()

private fun heatmapIntensity(value: Double, maximum: Double): Int {
    if (value <= 0.0 || maximum <= 0.0) return 0
    return when (value / maximum) {
        in 0.75..Double.POSITIVE_INFINITY -> 4
        in 0.5..<0.75 -> 3
        in 0.25..<0.5 -> 2
        in 0.0..<0.25 -> 1
        else -> 0
    }
}
