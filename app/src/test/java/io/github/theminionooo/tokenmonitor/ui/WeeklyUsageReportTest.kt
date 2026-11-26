package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HistoryAttribution
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyUsageReportTest {
    private val end = LocalDate.parse("2026-11-20")

    @Test
    fun reportKeepsRecordedZeroAndGapsInTheTrailingWeek() {
        val report = weeklyUsageReport(
            listOf(
                HistoryPoint("2026-11-13", 999, 99.0),
                HistoryPoint("2026-11-14", 0, 0.0),
                HistoryPoint("2026-11-20", 120, 0.5, unpricedTokens = 20),
                HistoryPoint("2026-11-21", 999, 99.0),
            ),
            end,
        )

        assertEquals(7L, report.calendarDays)
        assertEquals(5L, report.missingDays)
        assertEquals(listOf(0L, 120L), report.days.map { it.tokens })
        assertEquals(20L, report.days.sumOf { it.unpricedTokens })
        assertEquals(0.5, report.days.sumOf { it.costUsd }, 0.0)
    }

    @Test
    fun reportCannotExportArbitraryHubLabels() {
        val report = weeklyUsageReport(
            listOf(HistoryPoint(
                label = end.toString(),
                tokens = 100,
                costUsd = 0.3,
                perClient = mapOf("codex" to HistoryAttribution(40), "/private/project" to HistoryAttribution(60)),
                perModel = mapOf("private model alias" to HistoryAttribution(100)),
            )),
            end,
        )

        assertEquals(listOf(TrendSeries("codex"), TrendSeries(null)), report.series)
        assertEquals(60L, report.unattributedTokens)
        assertFalse(report.toString().contains("private"))
    }

    @Test
    fun sampleCoversTheLastSevenDaysWithConsistentAttribution() {
        val report = weeklyUsageReport(sampleUsageHistory(end), end)

        assertEquals(18_400_000L, report.totalTokens)
        assertEquals(0L, report.missingDays)
        assertEquals(0L, report.unattributedTokens)
        assertTrue(report.days.none { it.inconsistentAttribution })
    }
}
