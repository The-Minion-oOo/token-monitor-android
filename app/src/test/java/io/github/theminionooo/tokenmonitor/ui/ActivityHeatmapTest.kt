package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityHeatmapTest {
    private val endDate = LocalDate.parse("2026-09-06")

    @Test
    fun buildsTheDesktopStyleRollingSundayFirstGridWithCostIntensity() {
        val heatmap = buildActivityHeatmap(
            points = listOf(
                HistoryPoint("2026-06-01", tokens = 1, costUsd = 1.0),
                HistoryPoint("2026-06-02", tokens = 2, costUsd = 25.0),
                HistoryPoint("2026-06-03", tokens = 3, costUsd = 50.0),
                HistoryPoint("2026-06-04", tokens = 4, costUsd = 75.0),
                HistoryPoint("2026-06-05", tokens = 5, costUsd = 100.0),
                HistoryPoint("not-a-date", tokens = 999, costUsd = 999.0),
            ),
            endDate = endDate,
        )

        assertEquals(LocalDate.parse("2025-09-28"), heatmap.cells.first().date)
        assertEquals(0, heatmap.cells.first().row)
        assertEquals(4, heatmap.cells.first { it.date == LocalDate.parse("2026-06-04") }.intensity)
        assertEquals(3, heatmap.cells.first { it.date == LocalDate.parse("2026-06-03") }.intensity)
        assertEquals(2, heatmap.cells.first { it.date == LocalDate.parse("2026-06-02") }.intensity)
        assertEquals(1, heatmap.cells.first { it.date == LocalDate.parse("2026-06-01") }.intensity)
        assertEquals(0, heatmap.cells.first { it.date == LocalDate.parse("2026-05-31") }.intensity)
        assertEquals(5, heatmap.activeDays)
        assertTrue(heatmap.monthLabels.any { it.date == LocalDate.parse("2026-06-01") })
        assertEquals(1, heatmap.cells.first { it.date == LocalDate.parse("2026-06-01") }.row)
    }

    @Test
    fun fallsBackToTokenIntensityWhenOlderHubHistoryOmitsCosts() {
        val heatmap = buildActivityHeatmap(
            points = listOf(
                HistoryPoint("2026-09-04", tokens = 10, costUsd = 0.0),
                HistoryPoint("2026-09-05", tokens = 40, costUsd = 0.0),
            ),
            endDate = endDate,
        )

        assertEquals(2, heatmap.cells.first { it.date == LocalDate.parse("2026-09-04") }.intensity)
        assertEquals(4, heatmap.cells.first { it.date == LocalDate.parse("2026-09-05") }.intensity)
        assertEquals(2, heatmap.activeDays)
    }

    @Test
    fun keepsTheLatestValueWhenACompatibilityPayloadRepeatsADate() {
        val heatmap = buildActivityHeatmap(
            points = listOf(
                HistoryPoint("2026-09-05", tokens = 500, costUsd = 50.0),
                HistoryPoint("2026-09-05", tokens = 10, costUsd = 0.4),
                HistoryPoint("2026-09-06", tokens = 40, costUsd = 4.0),
            ),
            endDate = endDate,
        )

        assertEquals(1, heatmap.cells.first { it.date == LocalDate.parse("2026-09-05") }.intensity)
        assertEquals(4, heatmap.cells.first { it.date == LocalDate.parse("2026-09-06") }.intensity)
        assertEquals(2, heatmap.activeDays)
    }

    @Test
    fun letsTheOverviewSwitchBetweenTokenAndCostIntensity() {
        val points = listOf(
            HistoryPoint("2026-09-05", tokens = 100, costUsd = 1.0),
            HistoryPoint("2026-09-06", tokens = 10, costUsd = 10.0),
        )

        val tokens = buildActivityHeatmap(points, endDate, ActivityMetric.Tokens)
        val cost = buildActivityHeatmap(points, endDate, ActivityMetric.Cost)

        assertEquals(4, tokens.cells.first { it.date == LocalDate.parse("2026-09-05") }.intensity)
        assertEquals(1, tokens.cells.first { it.date == LocalDate.parse("2026-09-06") }.intensity)
        assertEquals(1, cost.cells.first { it.date == LocalDate.parse("2026-09-05") }.intensity)
        assertEquals(4, cost.cells.first { it.date == LocalDate.parse("2026-09-06") }.intensity)
    }
}
