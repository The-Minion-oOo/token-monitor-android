package io.github.theminionooo.tokenmonitor.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaAlertDetectorTest {
    private val start = Instant.parse("2026-11-20T09:00:00Z")
    private val detector = QuotaAlertDetector()

    @Test fun lowQuotaAlertsOnCrossingAndRearmsBelowEighty() {
        assertTrue(read(50.0, 0).isEmpty())
        assertEquals(setOf(QuotaAlert.Low), read(85.0, 1))
        assertTrue(read(75.0, 1).isEmpty()) // Same provider timestamp.
        assertTrue(read(82.0, 2).isEmpty())
        assertTrue(read(90.0, 3).isEmpty())
        assertTrue(read(79.0, 4).isEmpty())
        assertEquals(setOf(QuotaAlert.Low), read(86.0, 5))
    }

    @Test fun resetNeedsLowerUsageAndAProviderConfirmedNewCycle() {
        assertTrue(read(97.0, 0, resetsIn = 60).isEmpty())
        assertTrue(read(91.0, 70, resetsIn = 60).isEmpty())
        assertEquals(setOf(QuotaAlert.Reset), read(5.0, 80, resetsIn = 3_600))
    }

    @Test fun oldErrorAndDuplicateReadingsCannotTriggerAnAlert() {
        val now = start.plusSeconds(1)
        for (bad in listOf(account(95.0, 1).copy(stale = true), account(95.0, 1).copy(status = "error"), account(95.0, -3_600))) {
            read(50.0, 0)
            assertTrue(detector.observe(listOf(bad), now).isEmpty())
        }
        read(50.0, 0)
        assertTrue(detector.observe(listOf(account(95.0, 1), account(96.0, 1)), now).isEmpty())
    }

    @Test fun firstReadingForAnotherHubStartsSilently() {
        read(40.0, 0)
        detector.clear()
        assertTrue(read(99.0, 1).isEmpty())
    }

    private fun read(used: Double, seconds: Long, resetsIn: Long? = null): Set<QuotaAlert> =
        detector.observe(listOf(account(used, seconds, resetsIn)), start.plusSeconds(seconds))

    private fun account(used: Double, seconds: Long, resetsIn: Long? = null) = LimitAccount(
        provider = "codex", accountName = "", accountEmail = "", plan = "", status = "ok", sourceDeviceId = "desktop",
        updatedAt = start.plusSeconds(seconds).toString(), accountKey = "personal",
        windows = listOf(LimitWindow("session", "Session", used, null, null, resetsIn?.let { start.plusSeconds(it).toString() }.orEmpty(), "", "", "", true)),
    )
}
