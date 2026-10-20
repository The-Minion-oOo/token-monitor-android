package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DesktopV076Test {
    private fun response(name: String) = javaClass.getResource("/protocol/v0.76.0/$name.json")!!.readText()
    private val now = Instant.parse("2026-10-20T12:00:00Z").toEpochMilli()
    private fun modern() = HubProtocolParser.decodeStats(response("modern"))

    @Test fun `all five endpoints and stream retain released Hub usage`() {
        val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 0)
        assertTrue(snapshot.health.hubBuild.startsWith("sha256:"))
        assertEquals(1234L, snapshot.today.totalTokens)
        assertEquals(1234L, snapshot.stats.devices.single().periods.getValue("today").totalTokens)
        assertEquals(1234L, snapshot.history.daily.single().tokens)
        fun frame(name: String) = javaClass.getResource("/protocol/v0.76.0/$name.sse")!!.readText()
            .lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val full = HubStreamProtocol.normalizeComplete(frame("stats-stream"))
        val fresh = HubStreamProtocol.mergeFreshness(full, frame("stats-freshness"))
        val expected = HubProtocolParser.decodeStats(response("stats"))
        val actual = HubProtocolParser.decodeStats(fresh)
        assertEquals(expected.periods, actual.periods)
        assertEquals(expected.limits.providers, actual.limits.providers)
        assertEquals(expected.devices.single().periods, actual.devices.single().periods)
    }

    @Test fun `Antigravity parent links group with disjoint totals`() {
        val rows = groupSessions(modern().periods.getValue("today").sessions)
        val parent = rows.single { it.kind == SessionGroupKind.Conversation }
        assertEquals("antigravity", parent.display.client)
        assertEquals(850L, parent.totalTokens)
        assertEquals(1, parent.subagentCount)
        val child = groupSessions(parent.sessions.filter { it.parentSessionId.isNotBlank() }).single()
        assertTrue(child.subagent)
    }

    @Test fun `project models preserve complete partial and legacy attribution`() {
        val stats = modern()
        val daily = stats.periods.getValue("today").projects.single()
        assertEquals(150L, daily.models["deepseek-v4"])
        assertTrue(daily.matchesSearch("deepseek-v4"))
        val projects = stats.periods.getValue("allTime").projects.associateBy { it.label }
        assertFalse(projectShares(projects.getValue("Complete project"), true).any { it.unknown })
        val partial = projectShares(projects.getValue("Partial project"), true)
        assertEquals(40L, partial.single { it.unknown }.tokens)
        assertEquals(100L, partial.sumOf { it.tokens })
        assertEquals(listOf(ProjectShare("Unknown model", 100, true)), projectShares(projects.getValue("Legacy project"), true))
    }

    @Test fun `Grok Bot recent usage is idle unless an open turn is reported`() {
        val bot = modern().periods.getValue("today").sessions.single { it.client == "cursor" }
        assertEquals(SessionActivityState.Idle, sessionActivityState(bot, now))
        assertEquals(SessionActivityState.Running, sessionActivityState(bot.copy(turnEnded = false), now))
        assertEquals(SessionActivityState.Finished, sessionActivityState(bot.copy(turnEnded = true), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(bot.copy(turnEnded = false, lastUsedAt = "2026-10-20T11:00:00Z"), now))
        val aliased = bot.copy(id = "regular-id", modelTokens = mapOf("Display alias" to 150L))
        val evidence = aliased.copy(modelTokens = mapOf("grok-bot-default" to 150L))
        val row = groupSessions(listOf(aliased), listOf(evidence)).single()
        assertEquals(SessionActivityState.Idle, sessionRowActivity(row, now))
        assertTrue(isGrokBotSession(row.display))
        assertEquals(SessionActivityState.Running, sessionActivityState(aliased, now))
    }

    @Test fun `Argon stays separate while missing and exhausted quotas stay distinct`() {
        val windows = modern().limits.providers.single().windows.associateBy { it.label }
        assertEquals(65.0, windows.getValue("Gemini 4 Argon").remainingPercent!!, 0.0)
        assertNull(windows.getValue("Gemini Pro").remainingPercent)
        assertEquals(false, windows.getValue("Gemini Pro").showMeter)
        assertEquals(0.0, windows.getValue("Gemini Flash").remainingPercent!!, 0.0)
        assertEquals(true, windows.getValue("Gemini Flash").showMeter)
    }

    @Test fun `Grok reasoning and explicit classified zero survive fixed ranges`() {
        val session = modern().periods.getValue("today").sessions.single { it.client == "grok" }
        assertEquals(175L, session.totalTokens)
        assertEquals(75L, session.outputTokens)
        val history = HubProtocolParser.decodeSnapshot(response("health"), "{}", "{}", response("classified-history"), "{}", 0).history
        val day = history.daily.single()
        assertEquals(0L, day.perClient.getValue("grok").unclassifiedTokens)
        val relativeDay = day.copy(label = LocalDate.now().minusDays(1).toString())
        val snapshot = HubSnapshot(history = HubHistory(daily = listOf(relativeDay)))
        for (period in listOf(DashboardPeriod.Last7, DashboardPeriod.Last30)) {
            val usage = period.usage(snapshot)
            assertEquals(175L, usage.totalTokens)
            assertEquals(0L, usage.clientUnclassifiedTokens["grok"])
            assertEquals(75L, usage.clientOutputs["grok"])
        }
    }
}
