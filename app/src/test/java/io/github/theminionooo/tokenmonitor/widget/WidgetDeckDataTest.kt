package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class WidgetDeckDataTest {
    private val capturedAt = Instant.parse("2026-09-04T15:35:10Z").toEpochMilli()
    private val snapshot = HubProtocolParser.decodeSnapshot(
        healthRaw = resource("v0.54.0", "health.json"),
        statsRaw = resource("v0.54.0", "stats.json"),
        devicesRaw = resource("v0.54.0", "devices.json"),
        historyRaw = resource("v0.54.0", "history.json"),
        subscriptionsRaw = resource("v0.54.0", "subscriptions.json"),
        capturedAt = capturedAt,
    )

    @Test fun `deck has four stable manually ordered pages`() {
        assertEquals(
            listOf(WidgetDeckPage.Overview, WidgetDeckPage.Limits, WidgetDeckPage.Breakdown, WidgetDeckPage.Activity),
            WidgetDeckPage.entries,
        )
    }

    @Test fun `one snapshot prepares every page without inventing data`() {
        val data = prepareWidgetDeck(snapshot, WidgetSession(), capturedAt + 60_000, ZoneOffset.UTC, Locale.US)

        assertTrue(data.hasData)
        assertEquals(2, data.tools.size)
        assertEquals("codex", data.tools.first().name)
        assertEquals(2, data.models.size)
        assertEquals(1, data.limits.size)
        assertEquals(3, data.week.size)
        assertEquals(3, data.activeDays)
        assertFalse(data.liveEnabled)
        assertEquals("SAVED", data.status)
    }

    @Test fun `live stale offline and empty states stay distinct`() {
        val live = prepareWidgetDeck(
            snapshot,
            WidgetSession(enabled = true, connected = true, expiresAt = capturedAt + 3_600_000),
            capturedAt + 60_000,
            ZoneOffset.UTC,
            Locale.US,
        )
        assertEquals("LIVE", live.status)
        assertTrue(live.liveEnabled)

        val stale = prepareWidgetDeck(snapshot, WidgetSession(), capturedAt + 600_000, ZoneOffset.UTC, Locale.US)
        assertEquals("STALE", stale.status)

        val offline = prepareWidgetDeck(snapshot, WidgetSession(note = "Hub offline · retrying"), capturedAt + 60_000, ZoneOffset.UTC, Locale.US)
        assertEquals("OFFLINE", offline.status)
        assertTrue(offline.statusDescription.contains("Hub offline"))

        val empty = prepareWidgetDeck(null, WidgetSession(refreshing = true), capturedAt, ZoneOffset.UTC, Locale.US)
        assertFalse(empty.hasData)
        assertEquals("UPDATING", empty.status)
        assertTrue(empty.emptyMessage.startsWith("Refreshing"))
    }

    private fun resource(version: String, name: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/$version/$name"),
    ).bufferedReader().use { it.readText() }
}
