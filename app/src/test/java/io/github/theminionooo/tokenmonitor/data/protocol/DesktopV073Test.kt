package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopV073Test {
    private fun resource(name: String) = javaClass.getResource("/protocol/v0.73.0/$name")!!.readText()

    @Test fun `mixed timing and OMP titles survive every read surface`() {
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), resource("stats.json"), resource("devices.json"),
            resource("history.json"), resource("subscriptions.json"), 0)
        assertEquals("v0.73.0", snapshot.health.hubBuild)
        val today = snapshot.today
        assertEquals(1000L, today.totalTokens)
        assertEquals(1000L, snapshot.history.daily.single().tokens)
        assertEquals(today.sessions, snapshot.stats.devices.single().periods.getValue("today").sessions)
        assertEquals(200L, today.clients["claude"])
        assertEquals(300L, today.clients["goose"])
        val group = groupSessions(today.sessions).single { it.grouped }
        assertEquals(400L, group.totalTokens)
        assertEquals(100.0, group.usageMetrics.tokenRate!!, 0.0)
        assertNull(sessionTokenRate(today.sessions.single { it.id == "zero-timed" }))
        val omp = today.sessions.single { it.client == "omp" }
        assertEquals("\uE0A0 A1: Sample task", omp.title)
        assertEquals("A1: Sample task", sessionRowLabels(omp).title)
    }

    @Test fun `Cursor corrected percentage and explicit zero remain authoritative`() {
        val accounts = HubProtocolParser.decodeStats(resource("stats.json")).limits.providers
        assertEquals(87.0, accounts.first().windows.single().remainingPercent!!, 0.0)
        assertEquals(100.0, accounts.last().windows.single().remainingPercent!!, 0.0)
        assertEquals(0.0, accounts.last().windows.single().usedPercent!!, 0.0)
    }

    @Test fun `complete corrected zero replaces older counters before freshness`() {
        fun frame(name: String) = resource(name).lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val initial = HubStreamProtocol.normalizeComplete(frame("stats-stream.sse"))
        assertEquals(HubProtocolParser.decodeStats(resource("stats.json")), HubProtocolParser.decodeStats(initial))
        val root = Json.parseToJsonElement(initial).jsonObject
        val zero = buildJsonObject { put("totalTokens", 0); put("costUsd", 0); putJsonObject("sessions") {} }
        val correction = JsonObject(root + ("periods" to buildJsonObject { put("today", zero) })).toString()
        val complete = HubStreamProtocol.normalizeComplete("{\"stats\":$correction}")
        val fresh = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(complete, frame("stats-freshness.sse")))
        assertEquals(0L, fresh.periods.getValue("today").totalTokens)
        assertTrue(fresh.periods.getValue("today").sessions.isEmpty())
        assertEquals(100.0, fresh.limits.providers.last().windows.single().remainingPercent!!, 0.0)
    }
}
