package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.groupSessions
import io.github.theminionooo.tokenmonitor.ui.sessionRowMetricLabels
import io.github.theminionooo.tokenmonitor.ui.sessionRowPresentation
import org.junit.Assert.*
import org.junit.Test

class DesktopV072Test {
    @Test fun `included usage and prepaid balance remain separate`() {
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), resource("stats.json"),
            resource("devices.json"), resource("history.json"), resource("subscriptions.json"), 0)
        assertEquals("v0.72.0", snapshot.health.hubBuild)
        val accounts = snapshot.stats.limits.providers
        assertEquals(listOf("Premium", "Ultra", "Plus"), accounts.map { it.plan })
        val (included, prepaid) = accounts.first().windows
        assertEquals("Included usage", included.label)
        assertEquals("weekly", included.kind)
        assertEquals(63.0, included.remainingPercent!!, 0.0)
        assertEquals("2026-10-19T00:00:00.000Z", included.resetsAt)
        assertEquals("Prepaid balance", prepaid.label)
        assertEquals("credits", prepaid.metric)
        assertEquals("USD", prepaid.currency)
        assertEquals(14.46, prepaid.remaining!!, 0.0)
        assertEquals(false, prepaid.showMeter)
        assertNull(prepaid.usedPercent)
        assertNull(prepaid.remainingPercent)
        assertEquals("", prepaid.resetsAt)
        assertEquals("billing", accounts[1].windows.single().kind)
        assertEquals("2026-11-01T00:00:00.000Z", accounts[1].windows.single().resetsAt)
        assertEquals("", accounts[2].windows.single().resetsAt)
        assertEquals(5400L, snapshot.today.totalTokens)
        assertEquals(snapshot.today.sessions, snapshot.stats.devices.single().periods.getValue("today").sessions)
        assertEquals(5400L, snapshot.history.daily.single().tokens)
    }

    @Test fun `conversation metrics sum raw counters without borrowing duration`() {
        val today = HubProtocolParser.decodeStats(resource("stats.json")).periods.getValue("today")
        val group = groupSessions(today.sessions).single()
        assertEquals(5400L, group.totalTokens)
        assertEquals(4.0, group.costUsd, 0.0)
        assertEquals(10L, group.usageMetrics.messageCount)
        assertEquals(100.0 / 3, group.usageMetrics.cacheHitPercent!!, 0.0001)
        assertEquals(40.0, group.usageMetrics.tokenRate!!, 0.0)
        assertEquals("Cache hit 33% · 40 tok/s", sessionRowMetricLabels(group, 0))
        assertEquals("Sample conversation", sessionRowPresentation(group).title)
        assertTrue(sessionRowPresentation(group).meta.endsWith("10 messages"))
        assertEquals(0L, today.sessions.single { it.id == "child" }.timedOutputTokens)
    }

    @Test fun `stream and freshness preserve v0 72 values`() {
        val frame = resource("stats-stream.sse").lineSequence().first { it.startsWith("data: ") }.removePrefix("data: ")
        val initial = HubStreamProtocol.normalizeComplete(frame)
        assertEquals(HubProtocolParser.decodeStats(resource("stats.json")), HubProtocolParser.decodeStats(initial))
        val freshness = resource("stats-freshness.sse").lineSequence().first { it.startsWith("data: ") }.removePrefix("data: ")
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(initial, freshness))
        assertEquals(5400L, refreshed.periods.getValue("today").totalTokens)
        assertEquals(14.46, refreshed.limits.providers.first().windows.last().remaining!!, 0.0)
    }

    private fun resource(name: String) = javaClass.classLoader!!.getResource("protocol/v0.72.0/$name")!!.readText()
}
