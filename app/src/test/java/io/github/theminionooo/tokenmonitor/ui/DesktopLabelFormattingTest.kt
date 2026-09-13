package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import org.junit.Test

class DesktopLabelFormattingTest {
    private val now = 1_800_000_000_000L

    @Test
    fun resetCountdownUsesDesktopWording() {
        assertEquals("Reset 1h 59m", formatReset(isoAfter(hours = 1, minutes = 59), now))
        assertEquals("Reset 2d 21h", formatReset(isoAfter(hours = 69), now))
        assertEquals("Reset 5m", formatReset(isoAfter(minutes = 5), now))
        assertEquals("Reset now", formatReset(isoAfter(minutes = -3), now))
        assertEquals("", formatReset("", now))
        assertEquals("", formatReset("not a timestamp", now))
    }

    @Test
    fun boundaryCountdownUsesV056LifecycleWording() {
        val timestamp = isoAfter(hours = 2)
        assertEquals("Reset 2h 0m", formatBoundary(timestamp, "reset", now))
        assertEquals("Expires 2h 0m", formatBoundary(timestamp, "expiry", now))
        assertEquals("Changes in 2h 0m", formatBoundary(timestamp, "mixed", now))
        assertEquals("Changes now", formatBoundary(isoAfter(minutes = -1), "mixed", now))
        assertEquals("Reset 2h 0m", formatBoundary(timestamp, "", now))
    }

    @Test
    fun relativeAgeMatchesDesktopScale() {
        assertEquals("just now", formatRelativeAge(now - 2_000, now))
        assertEquals("38s ago", formatRelativeAge(now - 38_000, now))
        assertEquals("5m ago", formatRelativeAge(now - 5 * 60_000, now))
        assertEquals("2h 10m ago", formatRelativeAge(now - (2 * 60 + 10) * 60_000, now))
        assertEquals("3d 4h ago", formatRelativeAge(now - (3 * 24 + 4) * 3_600_000L, now))
        assertEquals("", formatRelativeAge(0, now))
    }

    @Test
    fun providerStatusLabelsFollowDesktop() {
        assertEquals("Not signed in", limitStatusLabel("notConfigured"))
        assertEquals("Sign in again", limitStatusLabel("unauthorized"))
        assertEquals("Limited", limitStatusLabel("rateLimited"))
        assertEquals("No quota reported", limitStatusLabel("ok"))
        assertEquals("Not available", limitStatusLabel(""))
    }

    private fun isoAfter(hours: Int = 0, minutes: Int = 0): String =
        java.time.Instant.ofEpochMilli(now + (hours * 60L + minutes) * 60_000L).toString()

    @Test
    fun `windows that share a name get the desktop period appended`() {
        fun window(label: String, kind: String) = LimitWindow(kind = kind, label = label, usedPercent = null, remainingPercent = 100.0, remaining = null, resetsAt = "", metric = "percent", currency = "", detail = "", showMeter = null)
        val spark5h = window("GPT-5.3-Codex-Spark", "session")
        val sparkWeekly = window("GPT-5.3-Codex-Spark", "weekly")
        val weekly = window("", "weekly")
        val windows = listOf(weekly, spark5h, sparkWeekly)
        assertEquals("Weekly", windowTitle(weekly, windows))
        assertEquals("GPT-5.3-Codex-Spark · 5-hour", windowTitle(spark5h, windows))
        assertEquals("GPT-5.3-Codex-Spark · Weekly", windowTitle(sparkWeekly, windows))
        assertEquals("GPT-5.3-Codex-Spark", windowTitle(spark5h, listOf(weekly, spark5h)))
    }
}
