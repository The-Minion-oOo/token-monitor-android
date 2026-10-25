package io.github.theminionooo.tokenmonitor.data.protocol

import androidx.compose.ui.graphics.Color
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.originalToolColor
import org.junit.Assert.*
import org.junit.Test

class DesktopV079Test {
    private fun response(name: String) = javaClass.getResource("/protocol/v0.79.0/$name")!!.readText()
    private fun account(name: String) = HubProtocolParser.decodeStats(response("$name.json")).limits.providers.single()

    @Test fun `Node and Worker reads and freshness keep their usage`() {
        for (prefix in listOf("", "worker/")) {
            val snapshot = HubProtocolParser.decodeSnapshot(response("${prefix}health.json"), response("${prefix}stats.json"),
                response("${prefix}devices.json"), response("${prefix}history.json"), response("${prefix}subscriptions.json"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
            fun frame(file: String) = response(file).lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
            val full = if (prefix.isEmpty()) frame("stats-stream.sse") else response("worker/stream-snapshot.json")
            val freshness = if (prefix.isEmpty()) frame("stats-freshness.sse") else response("worker/stream-freshness.json")
            val fresh = HubStreamProtocol.mergeFreshness(HubStreamProtocol.normalizeComplete(full), freshness)
            assertEquals(snapshot.stats.periods, HubProtocolParser.decodeStats(fresh).periods)
        }
    }

    @Test fun `OmO Native stays separate from Pi and Oh My Pi without adding reasoning twice`() {
        val stats = HubProtocolParser.decodeStats(response("omo-native.json"))
        val today = stats.periods.getValue("today")
        assertEquals(mapOf("senpi" to 180L, "pi" to 120L, "omp" to 140L), today.clients)
        assertEquals(440L, today.totalTokens)
        assertEquals(80L, today.sessions.single { it.client == "senpi" }.outputTokens)
        assertEquals(180L, today.sessions.single { it.client == "senpi" }.totalTokens)
        assertEquals("OmO Native", "senpi".displayName())
        assertEquals("Pi", "pi".displayName())
        assertEquals("Oh My Pi", "omp".displayName())
        assertEquals(Color(0xFF00D4FF), originalToolColor("senpi", Color.White))
        assertEquals(today.clients, stats.devices.single().periods.getValue("today").clients)
    }

    @Test fun `Console Go quotas and Zen balance keep separate meanings`() {
        val provider = account("opencode-console")
        assertEquals("opencode", provider.provider)
        assertEquals("ok", provider.status)
        val quota = provider.windows.filter { it.metric != "credits" }
        assertEquals(listOf("session", "weekly", "billing"), quota.map { it.kind })
        assertEquals(listOf(75.0, 60.0, 90.0), quota.map { it.remainingPercent })
        assertEquals("", quota.first().resetsAt)
        assertEquals("2026-11-25T00:00:00.000Z", quota.last().resetsAt)
        val balance = provider.windows.single { it.metric == "credits" }
        assertEquals("USD", balance.currency)
        assertEquals(27.86781005, balance.remaining!!, 0.0)
        assertEquals(false, balance.showMeter)
        assertNull(balance.remainingPercent)
    }

    @Test fun `no Go subscription still retains real zero and negative Zen balances`() {
        for ((file, amount) in listOf("opencode-zero" to 0.0, "opencode-negative" to -1.0)) {
            val provider = account(file)
            assertEquals("ok", provider.status)
            val window = provider.windows.single()
            assertEquals("credits", window.metric)
            assertEquals(amount, window.remaining!!, 0.0)
            assertEquals(false, window.showMeter)
            assertNull(window.usedPercent)
            assertNull(window.remainingPercent)
        }
    }

    @Test fun `unreported quota windows and reset times are not invented`() {
        val windows = account("opencode-missing").windows
        assertEquals(2, windows.size)
        assertEquals(listOf("session"), windows.filter { it.metric != "credits" }.map { it.kind })
        assertEquals("", windows.first().resetsAt)
        assertEquals(75.0, windows.first().remainingPercent!!, 0.0)
    }

    @Test fun `failed Console source does not hide healthy sibling results`() {
        val zen = account("opencode-go-unavailable")
        assertEquals("ok", zen.status)
        assertEquals("credits", zen.windows.single().metric)
        assertEquals(27.86781005, zen.windows.single().remaining!!, 0.0)
        val go = account("opencode-zen-unauthorized")
        assertEquals("ok", go.status)
        assertEquals(listOf(75.0, 60.0, 90.0), go.windows.map { it.remainingPercent })
        assertTrue(go.windows.none { it.metric == "credits" })
    }
}
