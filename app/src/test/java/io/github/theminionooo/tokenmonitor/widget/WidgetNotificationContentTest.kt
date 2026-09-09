package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetNotificationContentTest {
    @Test
    fun `fetch timestamp alone does not repost the notification`() {
        val snapshot = HubSnapshot(
            stats = HubStats(periods = mapOf("today" to UsagePeriod(totalTokens = 42, costUsd = 0.12))),
            capturedAt = 1,
        )
        val first = content(snapshot)
        val second = content(snapshot.copy(capturedAt = 2))

        assertEquals(first, second)
    }

    @Test
    fun `visible token change updates the notification`() {
        val snapshot = HubSnapshot(
            stats = HubStats(periods = mapOf("today" to UsagePeriod(totalTokens = 42, costUsd = 0.12))),
        )
        val changed = snapshot.copy(
            stats = snapshot.stats.copy(periods = mapOf("today" to snapshot.today.copy(totalTokens = 43))),
        )

        assertNotEquals(content(snapshot), content(changed))
    }

    private fun content(snapshot: HubSnapshot) = widgetNotificationContent(
        session = WidgetSession(enabled = true, connected = true, expiresAt = 3_600_000, snapshot = snapshot),
        live = true,
        now = 0,
        zoneId = ZoneOffset.UTC,
        locale = Locale.US,
    )
}
