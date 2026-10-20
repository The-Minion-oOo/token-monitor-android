package io.github.theminionooo.tokenmonitor.data.protocol

import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Responses produced by tools/check-hub-contract.mjs from the exact released desktop source. */
class ReleasedHubContractTest {
    @Test fun `released Hub endpoints and v2 stream preserve synthetic usage`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Run the pinned Hub contract harness to supply responses", !directory.isNullOrBlank())
        fun response(name: String) = File(directory!!, "$name.json").readText()
        val snapshot = HubProtocolParser.decodeSnapshot(
            response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 1,
        )
        assertTrue(snapshot.health.ok)
        assertEquals("hub", snapshot.health.role)
        assertTrue(snapshot.health.hubBuild.isNotBlank())
        assertEquals(1234L, snapshot.today.totalTokens)
        assertEquals(1234L, snapshot.today.clients["mcode"])
        assertEquals(321L, snapshot.today.unpricedTokens)
        assertEquals(321L, snapshot.today.clientUnpricedTokens["mcode"])
        assertEquals(321L, snapshot.today.modelUnpricedTokens["minimax-m2.5"])
        assertEquals(321L, snapshot.today.clientModelUnpricedTokens["mcode"]?.get("minimax-m2.5"))
        assertEquals(321L, snapshot.today.sessions.single().unpricedTokens)
        assertEquals(321L, snapshot.today.projects.single().unpricedTokens)
        assertEquals(321L, snapshot.history.daily.single().unpricedTokens)
        assertEquals(321L, snapshot.history.daily.single().perClient["mcode"]?.unpricedTokens)
        assertTrue(snapshot.stats.devices.any { it.id == "contract-desktop" })
        val streamed = HubStreamProtocol.normalizeComplete(response("stream-snapshot"))
        assertEquals(1234L, HubProtocolParser.decodeStats(streamed).periods["today"]?.totalTokens)
        val refreshed = HubStreamProtocol.mergeFreshness(streamed, response("freshness"))
        assertEquals(1234L, HubProtocolParser.decodeStats(refreshed).periods["today"]?.totalTokens)
        assertEquals(321L, HubProtocolParser.decodeStats(refreshed).periods["today"]?.unpricedTokens)
        val changed = HubStreamProtocol.normalizeComplete(response("stream-stats"))
        assertEquals(1235L, HubProtocolParser.decodeStats(changed).periods["today"]?.totalTokens)
        val shared = HubProtocolParser.decodeStats(response("titles-enabled"))
        assertEquals("Synthetic shared title", shared.periods.getValue("today").sessions.single().title)
        val revoked = HubProtocolParser.decodeStats(response("titles-revoked"))
        assertEquals("", revoked.periods.getValue("today").sessions.single().title)
        assertEquals(shared.periods.getValue("today").totalTokens, revoked.periods.getValue("today").totalTokens)
        val dots = HubProtocolParser.decodeStats(response("dots")).periods.getValue("today")
        assertTrue(dots.sessions.single().dotsObservedOnly)
        assertEquals(400L, dots.unpricedTokens)
        assertEquals(0.0, dots.costUsd, 0.0)
        val related = HubProtocolParser.decodeStats(response("relations")).periods.getValue("today").sessions.associateBy { it.id }
        val parent = related.getValue("sample-codex-parent")
        assertEquals("0b8f6c2e-3d41-4a6b-9c0e-5a1d2f7e8b01", parent.nativeSessionId)
        assertEquals(parent.nativeSessionId, related.getValue("sample-codex-continuation").nativeSessionId)
        assertEquals(false, parent.waitingForInput)
        assertEquals(true, related.getValue("sample-codex-waiting").waitingForInput)
        assertEquals(parent.id, related.getValue("sample-codex-subagent").parentSessionId)
        assertEquals("", related.getValue("sample-codex-subagent").parentClient)
        assertEquals("sample-t3-claude", related.getValue("sample-t3-codex").parentSessionId)
        assertEquals("claude", related.getValue("sample-t3-codex").parentClient)
        assertEquals("sample-t3-thread-1", related.getValue("sample-t3-codex").t3ThreadId)
        assertEquals("sample-t3-thread-1", related.getValue("sample-t3-claude").t3ThreadId)
        assertEquals("running", related.getValue("sample-t3-claude").liveActivity?.state)
        assertNull(related.getValue("sample-t3-claude").waitingForInput)
        val waiting = related.getValue("sample-claude-live").liveActivity
        assertEquals("waiting", waiting?.state)
        assertTrue(runCatching { Instant.parse(waiting?.observedAt.orEmpty()) }.isSuccess)
        assertNull(related.getValue("sand-subagent-sample-1").liveActivity)
        assertEquals(mapOf("grok-bot-1" to 50L), related.getValue("sample-cursor-bot").modelTokens)
        assertEquals(mapOf("gpt-5" to 600L), parent.modelTokens)
        val allowanceStats = HubProtocolParser.decodeStats(response("allowances"))
        assertEquals(1500L, allowanceStats.periods.getValue("today").clients["goose"])
        val notions = allowanceStats.limits.providers.filter { it.provider == "notion" }
        assertEquals(2, notions.size)
        assertTrue(notions.map { it.plan }.containsAll(listOf("Business", "Enterprise")))
        assertEquals(2, notions.map { it.accountKey }.distinct().size)
        notions.forEach { account ->
            assertEquals(listOf("Rolling", "Monthly"), account.windows.map { it.label })
            assertEquals(75.0, account.windows.first().remainingPercent!!, 0.0)
            assertTrue(account.windows.all { it.resetsAt.isNotBlank() })
        }
        val workbuddy = allowanceStats.limits.providers.filter { it.provider == "workbuddy" }
        assertEquals(2, workbuddy.size)
        assertEquals(setOf("ok", "unauthorized"), workbuddy.map { it.status }.toSet())
        val catpaw = HubProtocolParser.decodeStats(response("balance")).periods.getValue("today")
        assertEquals(1200L, catpaw.clients["catpaw"])
        assertEquals(mapOf("auto" to 1200L), catpaw.clientModels["catpaw"])
        assertEquals(1200L, catpaw.clientUnpricedTokens["catpaw"])
        assertEquals("sample-catpaw", catpaw.sessions.single().id)
        assertEquals("catpaw", catpaw.sessions.single().client)
        for ((name, amount, currency) in listOf(Triple("balance", 12.5, "CNY"), Triple("balance-zero", 0.0, "USD"), Triple("balance-negative", -3.2, "USD"))) {
            val window = HubProtocolParser.decodeStats(response(name)).limits.providers.single { it.provider == "minimax" }.windows.single()
            assertEquals(name, "billing", window.kind)
            assertEquals(name, "credits", window.metric)
            assertEquals(name, "Balance", window.label)
            assertEquals(name, amount, window.remaining!!, 0.0)
            assertEquals(name, currency, window.currency)
            assertNull(name, window.usedPercent)
            assertNull(name, window.remainingPercent)
            assertEquals(name, "", window.resetsAt)
        }
        fun cursor(name: String) = HubProtocolParser.decodeStats(response(name)).limits.providers.single { it.provider == "cursor" }
        val weekly = cursor("cursor-weekly")
        assertEquals("Premium", weekly.plan)
        assertEquals(listOf("Included usage", "Prepaid balance"), weekly.windows.map { it.label })
        assertEquals(63.0, weekly.windows.first().remainingPercent!!, 0.0)
        assertEquals("2026-10-19T00:00:00.000Z", weekly.windows.first().resetsAt)
        val prepaid = weekly.windows.last()
        assertEquals(14.46, prepaid.remaining!!, 0.0)
        assertEquals("USD", prepaid.currency)
        assertEquals(false, prepaid.showMeter)
        assertNull(prepaid.remainingPercent)
        assertEquals("Ultra", cursor("cursor-monthly").plan)
        assertEquals("billing", cursor("cursor-monthly").windows.single().kind)
        assertEquals("2026-11-01T00:00:00.000Z", cursor("cursor-monthly").windows.single().resetsAt)
        for (name in listOf("cursor-unknown-reset", "cursor-fallback")) {
            assertEquals("weekly", cursor(name).windows.single().kind)
            assertEquals("", cursor(name).windows.single().resetsAt)
        }
        assertEquals(listOf("Cursor Models", "Other Models", "Grok Bot", "On-demand spend"),
            cursor("cursor-legacy").windows.map { it.label })
        val metrics = HubProtocolParser.decodeStats(response("group-metrics")).periods.getValue("today")
        val group = io.github.theminionooo.tokenmonitor.ui.groupSessions(metrics.sessions).single()
        assertEquals(5400L, group.totalTokens)
        assertEquals(10L, group.usageMetrics.messageCount)
        assertEquals("Cache hit 33% · 40 tok/s", io.github.theminionooo.tokenmonitor.ui.sessionRowMetricLabels(group, 0))
        for ((name, used) in listOf("cursor-period" to 13.0, "cursor-period-zero" to 0.0,
            "cursor-sand-fallback" to 13.0, "cursor-monthly-fallback" to 30.0)) {
            val window = cursor(name).windows.single()
            assertEquals(name, used, window.usedPercent!!, 0.0)
            assertEquals(name, 100 - used, window.remainingPercent!!, 0.0)
        }
        assertTrue(cursor("cursor-weekly-unknown").windows.isEmpty())
        val timing = HubProtocolParser.decodeStats(response("matched-timing")).periods.getValue("today")
        val byId = timing.sessions.associateBy { it.id }
        assertEquals(200L, byId.getValue("partial").outputTokens)
        assertEquals(100L, byId.getValue("partial").timedOutputTokens)
        assertEquals(0L, byId.getValue("zero").timedOutputTokens)
        assertNull(io.github.theminionooo.tokenmonitor.ui.sessionTokenRate(byId.getValue("zero")))
        assertEquals(120.0, io.github.theminionooo.tokenmonitor.ui.sessionTokenRate(byId.getValue("reasoning"))!!, 0.0)
        assertEquals(220L, timing.timedOutputTokens)
        val corrected = HubProtocolParser.decodeStats(response("corrected-zero")).periods.getValue("today")
        assertEquals(0L, corrected.totalTokens)
        assertTrue(corrected.sessions.isEmpty())
        val modern = HubProtocolParser.decodeStats(response("modern"))
        val modernToday = modern.periods.getValue("today")
        assertEquals(150L, modernToday.projects.single().models["deepseek-v4"])
        val parentRow = io.github.theminionooo.tokenmonitor.ui.groupSessions(modernToday.sessions)
            .single { it.kind == io.github.theminionooo.tokenmonitor.ui.SessionGroupKind.Conversation }
        assertEquals(850L, parentRow.totalTokens)
        assertEquals(1, parentRow.subagentCount)
        val bot = modernToday.sessions.single { it.client == "cursor" }
        val botTime = Instant.parse(bot.lastUsedAt).toEpochMilli()
        assertEquals(io.github.theminionooo.tokenmonitor.ui.SessionActivityState.Idle,
            io.github.theminionooo.tokenmonitor.ui.sessionActivityState(bot, botTime))
        val argon = modern.limits.providers.single().windows.single { it.label == "Gemini 4 Argon" }
        assertEquals(65.0, argon.remainingPercent!!, 0.0)
        val missing = modern.limits.providers.single().windows.single { it.label == "Gemini Pro" }
        assertNull(missing.remainingPercent)
        assertEquals(false, missing.showMeter)
        val project = modern.periods.getValue("allTime").projects.single { it.label == "Partial project" }
        assertEquals(40L, io.github.theminionooo.tokenmonitor.ui.projectShares(project, true).single { it.unknown }.tokens)
        assertEquals(75L, modernToday.sessions.single { it.client == "grok" }.outputTokens)
        val classified = HubProtocolParser.decodeSnapshot("{}", "{}", "{}", response("classified-history"), "{}", 0).history.daily.single()
        assertEquals(0L, classified.perClient.getValue("grok").unclassifiedTokens)
    }
}
