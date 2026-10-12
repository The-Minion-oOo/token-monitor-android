package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.LiveActivity
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGroupingTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z").toEpochMilli()

    @Test fun `a subagent folds under its visible parent with summed totals`() {
        val parent = session("main", tokens = 100, cost = 1.0, minutesAgo = 5)
        val child = session("child", tokens = 40, cost = 0.5, minutesAgo = 2, parent = "main")
        val other = session("solo", tokens = 30)
        val rows = groupSessions(listOf(parent, child, other))
        assertEquals(listOf("group:session:codex:main", "codex:solo"), rows.map { it.key })
        val group = rows.first()
        assertEquals(SessionGroupKind.Conversation, group.kind)
        assertEquals(140L, group.totalTokens)
        assertEquals(1.5, group.costUsd, 1e-9)
        assertEquals(1, group.subagentCount)
        assertEquals(listOf("main" to true, "child" to false), group.members.map { it.session.id to it.main })
        assertEquals(parent, group.display)
        assertTrue(sessionRowPresentation(group).meta.startsWith("1 subagent · "))
    }

    @Test fun `a subagent without its parent in the list keeps its own labelled row`() {
        val rows = groupSessions(listOf(session("child", parent = "elsewhere")))
        assertFalse(rows.single().grouped)
        assertTrue(rows.single().subagent)
        assertTrue(sessionRowPresentation(rows.single()).meta.startsWith("Subagent"))
    }

    @Test fun `continuation files of one Codex conversation join without counting as subagents`() {
        val first = session("rollout-a", tokens = 10, minutesAgo = 30).copy(nativeSessionId = "native-1")
        val second = session("rollout-b", tokens = 20, minutesAgo = 1).copy(nativeSessionId = "native-1")
        val delegated = session("helper", tokens = 5, parent = "rollout-b")
        val group = groupSessions(listOf(first, second, delegated)).single()
        assertEquals("group:codex:native-1", group.key)
        assertEquals(35L, group.totalTokens)
        assertEquals(1, group.subagentCount)
        assertEquals(second, group.display)
        assertEquals(listOf(true, true, false), group.members.map { it.main })
    }

    @Test fun `T3 Code runs across tools share one conversation row`() {
        val claude = session("c1", client = "claude", tokens = 10).copy(t3ThreadId = "thread-9")
        val codex = session("x1", tokens = 15, minutesAgo = 0).copy(t3ThreadId = "thread-9")
        val group = groupSessions(listOf(claude, codex)).single()
        assertEquals("group:t3:thread-9", group.key)
        assertEquals(25L, group.totalTokens)
        assertEquals(0, group.subagentCount)
        assertEquals("", sessionRowPresentation(group).meta.substringAfterLast("subagent", ""))
    }

    @Test fun `background reviews form one Codex Auto Review row and never become subagents`() {
        val parent = session("main")
        val reviews = (1..3).map { session("review-$it", tokens = 7, parent = "main").copy(sessionKind = "background-review") }
        val rows = groupSessions(listOf(parent) + reviews)
        assertEquals(listOf("codex:main", "group:codex-auto-review"), rows.map { it.key })
        val summary = rows.last()
        assertEquals(21L, summary.totalTokens)
        assertEquals(SessionRowLabels("Codex Auto Review", "3 background runs"), sessionRowPresentation(summary))
    }

    @Test fun `Grok Bot records are grouped by sandbox id, positive bot model, flag or other-period evidence`() {
        val sandbox = session("sand-subagent-1", client = "cursor")
        val botModel = session("plain-1", client = "cursor").copy(modelTokens = mapOf("grok-bot-fast" to 3))
        val zeroModel = session("plain-2", client = "cursor").copy(modelTokens = mapOf("grok-bot-fast" to 0))
        val flaggedOff = session("sand-subagent-2", client = "cursor").copy(grokBotSession = false)
        val provenElsewhere = session("plain-3", client = "cursor")
        val evidence = listOf(provenElsewhere.copy(modelTokens = mapOf("grok-bot-fast" to 9)))
        val rows = groupSessions(listOf(sandbox, botModel, zeroModel, flaggedOff, provenElsewhere), evidence)
        val bot = rows.single { it.kind == SessionGroupKind.GrokBot }
        assertEquals(setOf("sand-subagent-1", "plain-1", "plain-3"), bot.members.map { it.session.id }.toSet())
        assertEquals(SessionRowLabels("Grok Bot", "3 activity records"), sessionRowPresentation(bot))
        assertEquals(setOf("cursor:plain-2", "cursor:sand-subagent-2"), rows.filter { !it.grouped }.map { it.key }.toSet())
    }

    @Test fun `conflicting parents and cycles never collapse identities or loop`() {
        val a = session("a", minutesAgo = 3).copy(nativeSessionId = "n")
        val b = session("b", minutesAgo = 2, parent = "x").copy(nativeSessionId = "n")
        val c = session("c", minutesAgo = 1, parent = "y").copy(nativeSessionId = "n")
        val x = session("x")
        val y = session("y")
        val rows = groupSessions(listOf(a, b, c, x, y))
        assertEquals(5, rows.sumOf { it.sessions.size })
        val loop = groupSessions(listOf(session("p", parent = "q"), session("q", parent = "p")))
        assertEquals(2, loop.sumOf { it.sessions.size })
    }

    @Test fun `a plain session id never aliases a native conversation id`() {
        val plain = session("native-1")
        val continuation = session("rollout", minutesAgo = 3).copy(nativeSessionId = "native-1")
        assertEquals(2, groupSessions(listOf(plain, continuation)).size)
    }

    @Test fun `search keeps whole groups and finds summary labels`() {
        val parent = session("main", tokens = 100)
        val child = session("child", tokens = 40, parent = "main")
        val bot = session("sand-subagent-1", client = "cursor")
        val rows = groupSessions(listOf(parent, child, bot))
        val byId = { id: String -> { s: SessionUsage -> s.id == id } }
        assertEquals(listOf(140L), searchSessionRows(rows, true, byId("child"), "child").map { it.totalTokens })
        assertEquals(SessionGroupKind.GrokBot, searchSessionRows(rows, true, { false }, "grok").single().kind)
        assertEquals(rows, searchSessionRows(rows, false, { false }, ""))
    }

    @Test fun `a repeated accounting key keeps its own row`() {
        val one = session("dup", tokens = 1)
        val two = session("dup", tokens = 2, minutesAgo = 4)
        assertEquals(listOf(1L, 2L), groupSessions(listOf(one, two)).map { it.totalTokens })
    }

    @Test fun `a group is running before waiting before its first member's state`() {
        val waitingParent = session("main", minutesAgo = 1).copy(waitingForInput = true)
        val finishedChild = session("child", minutesAgo = 1, parent = "main").copy(turnEnded = true)
        val group = groupSessions(listOf(waitingParent, finishedChild)).single()
        assertEquals(SessionActivityState.Waiting, sessionRowActivity(group, now))
        val running = groupSessions(listOf(waitingParent, finishedChild.copy(turnEnded = false))).single()
        assertEquals(SessionActivityState.Running, sessionRowActivity(running, now))
    }

    @Test fun `waiting comes from Codex questions and fresh provider observations only`() {
        val codex = session("q", minutesAgo = 2).copy(waitingForInput = true)
        assertEquals(SessionActivityState.Waiting, sessionActivityState(codex, now))
        assertEquals(SessionActivityState.Running, sessionActivityState(codex.copy(client = "claude"), now))
        assertEquals(SessionActivityState.Finished, sessionActivityState(codex.copy(turnEnded = true), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(codex.copy(lastUsedAt = iso(20)), now))

        val observed = { state: String, secondsAgo: Long ->
            session("live", client = "claude", minutesAgo = 60).copy(liveActivity = LiveActivity(state, Instant.ofEpochMilli(now - secondsAgo * 1000).toString()))
        }
        assertEquals(SessionActivityState.Waiting, sessionActivityState(observed("waiting", 10), now))
        assertEquals(SessionActivityState.Running, sessionActivityState(observed("running", 29), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(observed("waiting", 31), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(observed("unknown", 5), now))
        // The dashboard clock lags real time by up to 30 seconds, so a just-arrived observation can be ahead of it.
        assertEquals(SessionActivityState.Waiting, sessionActivityState(observed("waiting", -20), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(observed("waiting", -60), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(observed("waiting", 5).copy(client = "cursor"), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(observed("running", 5).copy(archived = true), now))
        assertEquals("Waiting for you", sessionActivityLabel(SessionActivityState.Waiting))
        assertNull(groupSessions(emptyList()).firstOrNull())
    }

    private fun iso(minutesAgo: Long) = Instant.ofEpochMilli(now - minutesAgo * 60_000).toString()

    private fun session(
        id: String,
        client: String = "codex",
        tokens: Long = 10,
        cost: Double = 0.0,
        minutesAgo: Long = 1,
        parent: String = "",
    ) = SessionUsage(
        id = id, client = client, projectLabel = "Example Project", totalTokens = tokens, costUsd = cost,
        modelNames = listOf("gpt-5"), messageCount = 0, startedAt = iso(minutesAgo + 5), lastUsedAt = iso(minutesAgo),
        parentSessionId = parent, modelTokens = mapOf("gpt-5" to tokens),
    )
}
