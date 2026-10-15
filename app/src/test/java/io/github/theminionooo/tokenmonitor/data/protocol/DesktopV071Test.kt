package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.upstreamToolAsset
import io.github.theminionooo.tokenmonitor.ui.vendorOf
import io.github.theminionooo.tokenmonitor.ui.windowTitle
import org.junit.Assert.*
import org.junit.Test

class DesktopV071Test {
    private fun resource(name: String) = javaClass.getResource("/protocol/v0.71.0/$name")!!.readText()

    @Test fun `Goose usage survives every read surface and stream updates`() {
        val source = resource("stats.json")
        val snapshot = HubProtocolParser.decodeSnapshot(resource("health.json"), source, resource("devices.json"),
            resource("history.json"), resource("subscriptions.json"), 0)
        assertEquals("v0.71.0", snapshot.health.hubBuild)
        assertEquals(1500L, snapshot.today.clients["goose"])
        assertEquals("goose", snapshot.today.sessions.single().client)
        assertEquals(0.05, snapshot.today.costUsd, 0.0)
        assertEquals(listOf("goose"), snapshot.stats.devices.single().trackedClients)
        assertEquals(1500L, snapshot.history.daily.single().perClient["goose"]?.tokens)
        fun frame(name: String) = resource(name).lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val stats = HubProtocolParser.decodeStats(source)
        assertEquals(stats, HubProtocolParser.decodeStatsStreamEvent(frame("stats-stream.sse")))
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(source, frame("stats-freshness.sse")))
        assertEquals("2026-10-14T20:02:00.000Z", refreshed.updatedAt)
        assertEquals(stats.periods, refreshed.periods)
        assertEquals(stats.limits.providers, refreshed.limits.providers)
    }

    @Test fun `Notion rolling and monthly allowances and WorkBuddy accounts stay separate`() {
        val accounts = HubProtocolParser.decodeStats(resource("stats.json")).limits.providers
        val notion = accounts.filter { it.provider == "notion" }
        assertEquals(2, notion.size)
        assertNotEquals(notion[0].accountKey, notion[1].accountKey)
        assertEquals(listOf("Business", "Enterprise"), notion.map { it.plan })
        val windows = notion.first().windows
        assertEquals(listOf("session", "billing"), windows.map { it.kind })
        assertEquals(listOf("Rolling", "Monthly"), windows.map { windowTitle(it, windows) })
        assertEquals(75.0, windows.first().remainingPercent!!, 0.0)
        assertEquals(60.0, windows.last().remainingPercent!!, 0.0)
        assertEquals("2026-11-01T00:00:00.000Z", windows.last().resetsAt)
        assertEquals("", notion.last().windows.single().resetsAt)
        val workbuddy = accounts.filter { it.provider == "workbuddy" }
        assertEquals(2, workbuddy.size)
        assertNotEquals(workbuddy[0].accountKey, workbuddy[1].accountKey)
        assertEquals(listOf("ok", "unauthorized"), workbuddy.map { it.status })
        assertEquals(700.0, workbuddy.first().windows.single().remaining!!, 0.0)
    }

    @Test fun `new provider labels and monochrome marks keep their own identity`() {
        assertEquals("Goose", "goose".displayName())
        assertEquals("Notion AI", "notion".providerLabel())
        assertEquals("goose", vendorOf("goose"))
        assertEquals("notion", vendorOf("notion"))
        assertNotNull(upstreamToolAsset("goose"))
        assertNotNull(upstreamToolAsset("notion"))
        assertNotEquals(upstreamToolAsset("goose"), upstreamToolAsset("notion"))
    }
}
