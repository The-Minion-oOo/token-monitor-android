package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubProtocolParserTest {
    @Test
    fun `v0 54 fixture maps every dashboard surface`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            healthRaw = resource("health.json"),
            statsRaw = resource("stats.json"),
            devicesRaw = resource("devices.json"),
            historyRaw = resource("history.json"),
            subscriptionsRaw = resource("subscriptions.json"),
            capturedAt = 1_725_464_110_000,
        )

        assertTrue(snapshot.health.ok)
        assertEquals("node-hub", snapshot.health.runtime)
        assertEquals(125_430, snapshot.today.totalTokens)
        assertEquals(2, snapshot.today.clients.size)
        assertEquals(60_000, snapshot.today.clientCacheReads.getValue("codex"))
        assertEquals(9_000, snapshot.today.clientOutputs.getValue("codex"))
        assertEquals(60_000, snapshot.today.modelCacheReads.getValue("gpt-5"))
        assertEquals("Token Monitor", snapshot.today.projects.single().label)
        assertEquals(81_300, snapshot.today.projects.single().clients.getValue("codex"))
        assertEquals("session-001", snapshot.today.sessions.single().id)
        assertEquals(3, snapshot.today.sessions.single().messageCount)
        assertEquals("Studio", snapshot.stats.devices.single().hostname)
        assertEquals("11 24H2", snapshot.stats.devices.single().osVersion)
        assertEquals(125_430, snapshot.stats.devices.single().history.daily.single().tokens)
        assertEquals(64.0, snapshot.stats.limits.providers.single().windows.single().remainingPercent!!, 0.001)
        assertEquals(3, snapshot.history.daily.size)
        assertEquals(71_310, snapshot.history.daily.first().perClient.getValue("codex").tokens)
        assertEquals(50_000, snapshot.history.daily.first().perModel.getValue("gpt-5").cacheReadTokens)
        assertTrue(snapshot.history.daily.first().tokenComponentsAvailable)
        assertEquals("Plus", snapshot.subscriptions.entries.single().planName)
        assertFalse(snapshot.fromCache)
    }

    @Test
    fun `unknown and missing optional fields are safe`() {
        val stats = HubProtocolParser.decodeStats(
            """{"periods":{"today":{"totalTokens":9,"newField":{"nested":true}}},"unknown":42}""",
        )

        assertEquals(9, stats.periods.getValue("today").totalTokens)
        assertTrue(stats.devices.isEmpty())
        assertTrue(stats.limits.providers.isEmpty())
        assertTrue(stats.historyPreview.daily.isEmpty())
    }

    @Test
    fun `v0 54 stream snapshot maps stats and ignores envelope fields`() {
        val data = resource("stats-stream.sse")
            .lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }

        val stats = HubProtocolParser.decodeStatsStreamEvent(data)

        assertNotNull(stats)
        assertEquals(125_430, stats!!.periods.getValue("today").totalTokens)
        assertEquals("2026-09-03T12:00:00.000Z", stats.subscriptionsUpdatedAt)
    }

    @Test
    fun `malformed stream event is ignored`() {
        assertEquals(null, HubProtocolParser.decodeStatsStreamEvent("not json"))
    }

    private fun resource(name: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/v0.54.0/$name"),
    ).bufferedReader().use { it.readText() }
}
