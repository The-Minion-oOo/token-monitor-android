package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import io.github.theminionooo.tokenmonitor.widget.remainingPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaPercentTest {
    @Test fun roundsHalfUpLikeTheDesktopInsteadOfTruncating() {
        assertEquals(1, quotaPercentLeft(0.9, 100.0))
        assertEquals(100, quotaPercentLeft(99.6, 100.0))
        assertEquals(42, quotaPercentLeft(42.4, 100.0))
        assertEquals(43, quotaPercentLeft(42.5, 100.0))
        assertEquals(0, quotaPercentLeft(0.0, 100.0))
        assertEquals(50, quotaPercentLeft(25.0, 50.0))
    }

    @Test fun everyHalfPointRoundsUpDespiteBinaryFractions() {
        (0..99).forEach { whole -> assertEquals("$whole.5", whole + 1, quotaPercentLeft(whole + 0.5, 100.0)) }
    }

    @Test fun clampsToTheValidRange() {
        assertEquals(100, quotaPercentLeft(104.0, 100.0))
        assertEquals(0, quotaPercentLeft(-20.0, 100.0))
        assertEquals(100, quotaPercentLeft(Double.MAX_VALUE, 1.0))
    }

    @Test fun missingOrUnusableInputsHaveNoPercent() {
        assertNull(quotaPercentLeft(50.0, 0.0))
        assertNull(quotaPercentLeft(50.0, -100.0))
        assertNull(quotaPercentLeft(Double.NaN, 100.0))
        assertNull(quotaPercentLeft(50.0, Double.NaN))
        assertNull(quotaPercentLeft(Double.POSITIVE_INFINITY, 100.0))
        assertNull(quotaPercentLeft(50.0, Double.POSITIVE_INFINITY))
        assertNull(quotaPercentLeft(null, 100.0))
        assertNull(quotaPercentLeft(50.0, null))
    }

    @Test fun aWindowReportsPercentLeftDirectlyOrAsTheComplementOfUsed() {
        assertEquals(1, quotaPercentLeft(window(remainingPercent = 0.9)))
        assertEquals(100, quotaPercentLeft(window(remainingPercent = 104.0)))
        assertEquals(0, quotaPercentLeft(window(remainingPercent = -20.0)))
        assertEquals(75, quotaPercentLeft(window(usedPercent = 25.0)))
        assertEquals(0, quotaPercentLeft(window(usedPercent = 120.0)))
        assertEquals(30, quotaPercentLeft(window(remainingPercent = Double.NaN, usedPercent = 70.0)))
        assertNull(quotaPercentLeft(window()))
        assertNull(quotaPercentLeft(window(remainingPercent = Double.NaN)))
    }

    @Test fun theUsageWidgetShowsTheSameWholeNumberAsTheApp() {
        assertEquals(1.0, remainingPercent(window(remainingPercent = 0.9))!!, 0.0)
        assertEquals(100.0, remainingPercent(window(remainingPercent = 99.6))!!, 0.0)
        assertEquals(0.0, remainingPercent(window(usedPercent = 140.0))!!, 0.0)
        assertNull(remainingPercent(window()))
    }

    private fun window(remainingPercent: Double? = null, usedPercent: Double? = null) = LimitWindow(
        kind = "weekly",
        label = "Weekly",
        usedPercent = usedPercent,
        remainingPercent = remainingPercent,
        remaining = null,
        resetsAt = "",
        metric = "percent",
        currency = "",
        detail = "",
        showMeter = true,
    )
}
