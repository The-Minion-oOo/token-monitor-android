package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.*
import org.junit.Test

class SnapshotRecoveryTest {
    @Test fun `old SSE cache restores the same totals as a live event`() {
        val event = resource("stats-stream.sse").lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), event, resource("devices.json"), resource("history.json"), resource("subscriptions.json"), 1000, true)
        assertEquals(125430L, snapshot.today.totalTokens)
        assertEquals(4021451L, snapshot.allTime.totalTokens)
        assertTrue(snapshot.fromCache)
        assertFalse(HubProtocolParser.normalizeStatsJson(event).contains("\"reason\""))
    }

    @Test fun `cached device details cannot resurrect a removed device`() {
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), """{"periods":{},"devices":[]}""", resource("devices.json"), null, null, 1000, true)
        assertTrue(snapshot.stats.devices.isEmpty())
    }

    @Test fun `fresh preview replaces an older historical observation`() {
        val stats = """{"periods":{},"historyPreview":{"daily":[{"date":"2026-09-04","tokens":999999,"cost":8.0}]}}"""
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), stats, null, resource("history.json"), null, 1000)
        assertEquals(999999L, snapshot.history.daily.last().tokens)
        assertEquals(3, snapshot.history.daily.size)
    }

    @Test fun `tool model attribution never uses global model totals`() {
        val stats = HubProtocolParser.decodeStats("""{"periods":{"today":{"models":{"gpt-5":900},"clientModels":{"codex":{"gpt-5":200},"opencode":{"gpt-5":700}},"clientModelCosts":{"codex":{"gpt-5":0.25}}}}}""")
        val period = stats.periods.getValue("today")
        assertEquals(200L, period.clientModels.getValue("codex").getValue("gpt-5"))
        assertEquals(700L, period.clientModels.getValue("opencode").getValue("gpt-5"))
        assertEquals(0.25, period.clientModelCosts.getValue("codex").getValue("gpt-5"), 0.0001)
    }

    private fun resource(name: String) = checkNotNull(javaClass.classLoader?.getResourceAsStream("protocol/v0.54.0/$name")).bufferedReader().use { it.readText() }
}
