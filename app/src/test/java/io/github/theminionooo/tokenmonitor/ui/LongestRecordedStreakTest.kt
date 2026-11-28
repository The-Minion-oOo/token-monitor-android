package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LongestRecordedStreakTest {
    private val today = LocalDate.parse("2028-03-07")

    @Test fun longestCanBeAnOlderRunAcrossLeapDay() {
        val history = listOf(
            active("2028-03-07"), active("2028-02-29"), active("2028-02-28"),
            active("2028-03-06"), active("2028-03-01"), active("2028-02-27"),
        )
        assertEquals(2, currentStreak(history, today))
        assertEquals(4, longestRecordedStreak(history, today))
    }

    @Test fun missingAndZeroTokenDaysBreakRuns() {
        val history = listOf(
            active("2028-02-28"), HistoryPoint(label = "2028-02-29", tokens = 0, costUsd = 3.0),
            active("2028-03-01"), active("2028-03-03"), active("2028-03-04"),
        )
        assertEquals(2, longestRecordedStreak(history, today))
    }

    @Test fun duplicateAndInvalidDatesDoNotAddDays() {
        val history = listOf(
            active("2028-03-01"), active("2028-03-02T12:00:00Z"),
            active("2028-03-02"), active("invalid"),
        )
        assertEquals(2, longestRecordedStreak(history, today))
        val revised = history + HistoryPoint(label = "2028-03-02", tokens = 0, costUsd = 0.0)
        assertEquals(1, longestRecordedStreak(revised, today))
    }

    @Test fun acceptsTheHubsNextCalendarDayAndIgnoresLaterDates() {
        val history = listOf(
            active("2028-03-06"), active("2028-03-07"), active("2028-03-08"),
            active("2028-03-09"), active("2028-03-10"),
        )
        assertEquals(3, longestRecordedStreak(history, today))
        assertEquals(0, longestRecordedStreak(emptyList(), today))
    }

    private fun active(day: String) = HistoryPoint(label = day, tokens = 1, costUsd = 0.0)
}
