package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPresentationTest {
    private val now = Instant.parse("2026-09-21T12:00:00Z").toEpochMilli()

    @Test fun `recent unfinished and finished turns stay distinct`() {
        assertEquals(SessionActivityState.Running, sessionActivityState(session("2026-09-21T11:59:30Z"), now))
        assertEquals(SessionActivityState.Running, sessionActivityState(session("2026-09-21T11:59:30Z", false), now))
        assertEquals(SessionActivityState.Finished, sessionActivityState(session("2026-09-21T11:59:30Z", true), now))
        assertEquals(SessionActivityState.Idle, sessionActivityState(session("2026-09-21T11:49:59Z"), now))
    }

    @Test fun `context requires both values and remains visible after a recent turn finishes`() {
        val finished = session("2026-09-21T11:59:30Z", true).copy(contextTokens = 190_000, contextWindow = 200_000)
        assertEquals(SessionContext(percentUsed = 95, percentLeft = 5), sessionContextForRow(finished, now))
        assertNull(sessionContextForRow(finished.copy(contextWindow = 0), now))
        assertNull(sessionContextForRow(finished.copy(lastUsedAt = "2026-09-21T11:00:00Z"), now))
    }

    private fun session(lastUsedAt: String, turnEnded: Boolean? = null) = SessionUsage(
        id = "session-1",
        client = "codex",
        projectLabel = "Token Monitor",
        totalTokens = 10,
        costUsd = 0.0,
        modelNames = listOf("gpt-6-astra"),
        messageCount = 1,
        startedAt = "2026-09-21T11:50:00Z",
        lastUsedAt = lastUsedAt,
        turnEnded = turnEnded,
    )
}
