package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.widget.WidgetLayout
import io.github.theminionooo.tokenmonitor.widget.widgetLayout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsageHistoryTest {
    private val today = LocalDate.of(2026, 9, 7)
    @Test fun `seven calendar days do not mean seven recorded days`() {
        val history = (1L..7).map { HistoryPoint(today.minusDays(it * 3).toString(), 10, 1.0) }
        assertEquals(2, historyInRange(history, today.minusDays(6), today).size)
    }
    @Test fun `comparison excludes today and compares equal complete windows`() {
        val history = (0L..14).map { HistoryPoint(today.minusDays(it).toString(), if (it == 0L) 999 else if (it <= 7) 20 else 10, 1.0) }
        val result = compareUsage(history, today)
        assertTrue(result.complete)
        assertEquals(70L, result.tokenDelta)
        assertEquals(100.0, result.percentChange!!, 0.001)
    }
    @Test fun `incomplete and zero baselines have no percentage estimate`() {
        assertNull(compareUsage(listOf(HistoryPoint("2026-09-06", 10, 1.0)), today).percentChange)
        val history = (1L..14).map { HistoryPoint(today.minusDays(it).toString(), 0, 0.0) }
        assertNull(compareUsage(history, today).percentChange)
    }
    @Test fun `offline yesterday remains yesterday and keeps model attribution`() {
        val snapshot = HubSnapshot(stats = HubStats(updatedAt = "2026-09-06T12:00:00Z", periods = mapOf("today" to UsagePeriod(totalTokens = 42, models = mapOf("gpt-5" to 42)))), fromCache = true)
        val result = usageHistory(snapshot)
        assertEquals("2026-09-06", result.single().label)
        assertEquals(42L, result.single().perModel.getValue("gpt-5").tokens)
        assertTrue(historyInRange(result, today, today).isEmpty())
    }
    // Noon in the test machine's zone, so the phone's date does not depend on where the suite runs.
    private fun phoneNoon(date: String) = LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toString()
    private fun liveSnapshot(phoneDate: String, live: Long, rows: List<HistoryPoint>) = HubSnapshot(
        stats = HubStats(updatedAt = phoneNoon(phoneDate), periods = mapOf("today" to UsagePeriod(totalTokens = live, costUsd = live / 10.0, models = mapOf("gpt-5" to live))),
            historyPreview = HubHistory(daily = rows)),
    )

    @Test fun `live today replaces the same dated row and keeps its activity`() {
        val rows = listOf(HistoryPoint("2026-10-07", 500, 5.0), HistoryPoint("2026-10-08", 100, 1.0, messages = 7, activeTimeMs = 9_000))
        val result = usageHistory(liveSnapshot("2026-10-08", 250, rows))
        assertEquals(listOf("2026-10-07", "2026-10-08"), result.map { it.label })
        assertEquals(500L, result[0].tokens)
        assertEquals(250L, result[1].tokens)
        assertEquals(7L, result[1].messages)
        assertEquals(9_000L, result[1].activeTimeMs)
        assertEquals(250L, result[1].perModel.getValue("gpt-5").tokens)
    }

    @Test fun `live today starts a new row when the hub has none for the day yet`() {
        val result = usageHistory(liveSnapshot("2026-10-08", 30, listOf(HistoryPoint("2026-10-07", 500, 5.0))))
        assertEquals(listOf("2026-10-07", "2026-10-08"), result.map { it.label })
        assertEquals(30L, result.last().tokens)
    }

    @Test fun `a hub already on the next date does not overwrite the phone's full day`() {
        val rows = listOf(HistoryPoint("2026-10-08", 900, 9.0, messages = 3), HistoryPoint("2026-10-09", 40, 0.4, messages = 2, activeTimeMs = 1_000))
        val result = usageHistory(liveSnapshot("2026-10-08", 40, rows))
        assertEquals(listOf("2026-10-08", "2026-10-09"), result.map { it.label })
        assertEquals(900L, result[0].tokens)
        assertEquals(3L, result[0].messages)
        assertEquals(40L, result[1].tokens)
        assertEquals(2L, result[1].messages)
        assertEquals(40L, result[1].perModel.getValue("gpt-5").tokens)
    }

    @Test fun `a freshness frame past midnight does not double count the hub's last row`() {
        val rows = listOf(HistoryPoint("2026-10-07", 500, 5.0), HistoryPoint("2026-10-08", 800, 8.0, messages = 4))
        val result = usageHistory(liveSnapshot("2026-10-09", 800, rows))
        assertEquals(listOf("2026-10-07", "2026-10-08"), result.map { it.label })
        assertEquals(800L, result.last().tokens)
        assertEquals(4L, result.last().messages)
        assertEquals(800L, result.last().perModel.getValue("gpt-5").tokens)
    }

    @Test fun `a new day with different totals stays on the phone date`() {
        val rows = listOf(HistoryPoint("2026-10-08", 800, 8.0))
        val result = usageHistory(liveSnapshot("2026-10-09", 20, rows))
        assertEquals(listOf("2026-10-08", "2026-10-09"), result.map { it.label })
        assertEquals(800L, result[0].tokens)
        assertEquals(20L, result[1].tokens)
    }

    @Test fun `a zero live total never erases a real row`() {
        val rows = listOf(HistoryPoint("2026-10-08", 800, 8.0, messages = 4))
        assertEquals(800L, usageHistory(liveSnapshot("2026-10-08", 0, rows)).single().tokens)
        val ahead = rows + HistoryPoint("2026-10-09", 60, 0.6)
        assertEquals(listOf(800L, 60L), usageHistory(liveSnapshot("2026-10-08", 0, ahead)).map { it.tokens })
        val empty = usageHistory(liveSnapshot("2026-10-08", 0, listOf(HistoryPoint("2026-10-08", 0, 0.0))))
        assertEquals(0L, empty.single().tokens)
    }

    @Test fun `widget layout responds to both width and height`() {
        assertEquals(WidgetLayout.Wide, widgetLayout(300, 120))
        assertEquals(WidgetLayout.Overview, widgetLayout(250, 370))
        assertEquals(WidgetLayout.Large, widgetLayout(300, 440))
        assertEquals(WidgetLayout.Portrait, widgetLayout(180, 300))
        assertEquals(WidgetLayout.Wide, widgetLayout(300, 120, 1.3f))
        assertEquals(WidgetLayout.Wide, widgetLayout(240, 240, 1.3f))
        assertEquals(WidgetLayout.Overview, widgetLayout(240, 325, 1.3f))
        assertEquals(WidgetLayout.Compact, widgetLayout(180, 250, 1.3f))
    }

    @Test fun `sparse preview keeps matching attribution but never stale attribution`() {
        val saved = HistoryPoint("2026-09-06", 42, 1.0, outputTokens = 12,
            unclassifiedTokens = 30, tokenComponentsAvailable = true,
            perModel = mapOf("gpt-5" to HistoryAttribution(tokens = 42)))
        val sparse = HistoryPoint("2026-09-06", 42, 1.0)
        val matching = mergeUsageHistory(listOf(saved), listOf(sparse)).single()
        assertEquals(saved.perModel, matching.perModel)
        assertEquals(12L, matching.outputTokens)
        assertEquals(30L, matching.unclassifiedTokens)
        assertTrue(matching.tokenComponentsAvailable)

        val changed = mergeUsageHistory(listOf(saved), listOf(sparse.copy(tokens = 55))).single()
        assertTrue(changed.perModel.isEmpty())
        assertEquals(0L, changed.outputTokens)
        assertFalse(changed.tokenComponentsAvailable)
    }

    @Test fun `device rolling totals do not count an old today twice`() {
        val yesterday = LocalDate.now().minusDays(1)
        val device = DeviceUsage(id = "test", hostname = "test", platform = "windows", osName = "", osVersion = "", receivedAt = "", ageMs = null,
            stale = true, syncUploadIntervalMs = null, historyAvailable = true, trackedClients = emptyList(),
            updatedAt = "${yesterday}T12:00:00Z", periods = mapOf("today" to UsagePeriod(totalTokens = 42)),
            history = HubHistory(daily = listOf(HistoryPoint(yesterday.toString(), 42, 0.0))))
        assertEquals(42L, DashboardPeriod.Last7.usage(device).totalTokens)
    }

    @Test fun `missing attribution cannot invent a model change`() {
        val history = (1L..14).map { HistoryPoint(today.minusDays(it).toString(), 10, 0.0,
            perModel = if (it <= 7) mapOf("model" to HistoryAttribution(tokens = 10)) else emptyMap()) }
        assertTrue(compareUsage(history, today).complete)
        assertTrue(compareUsage(history, today).drivers(byModel = true).isEmpty())
    }
}
