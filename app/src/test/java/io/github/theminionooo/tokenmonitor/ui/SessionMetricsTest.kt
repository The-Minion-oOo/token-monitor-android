package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import org.junit.Assert.*
import org.junit.Test

class SessionMetricsTest {
    private fun session(id: String) = SessionUsage(id, "Sample conversation", "codex", "Sample project", 100, 0.0,
        listOf("gpt-5"), 0, "2026-10-16T12:00:00Z", "2026-10-16T12:01:00Z")

    @Test fun `empty and unreported metrics are unknown`() {
        val empty = sessionMetrics(emptyList())
        assertEquals(0L, empty.messageCount)
        assertNull(empty.cacheHitPercent)
        assertNull(empty.tokenRate)
        assertNull(sessionMetrics(listOf(session("a"))).cacheHitPercent)
    }

    @Test fun `cache writes make a reported zero hit rate and count in denominator`() {
        val metrics = sessionMetrics(listOf(session("a").copy(inputTokens = 100, cacheWriteTokens = 900)))
        assertEquals(0.0, metrics.cacheHitPercent!!, 0.0)
        assertEquals("Cache hit 0%", sessionCounterLabels(metrics.cacheHitPercent, metrics.tokenRate))
        assertEquals(50.0, sessionMetrics(listOf(session("a").copy(inputTokens = 100, cacheReadTokens = 200,
            cacheWriteTokens = 100))).cacheHitPercent!!, 0.0)
    }

    @Test fun `negative counters and untimed output cannot inflate sibling rate`() {
        val invalid = session("a").copy(messageCount = -1, inputTokens = -10, cacheReadTokens = -20,
            cacheWriteTokens = -30, outputTokens = 5000, timedOutputTokens = 5000, timedDurationMs = 0)
        val timed = session("b").copy(messageCount = 2, outputTokens = 100, timedOutputTokens = 999, timedDurationMs = 2000)
        val metrics = sessionMetrics(listOf(invalid, timed))
        assertEquals(2L, metrics.messageCount)
        assertEquals(50.0, metrics.tokenRate!!, 0.0)
        assertNull(metrics.cacheHitPercent)
        assertNull(sessionMetrics(listOf(timed.copy(timedDurationMs = -1))).tokenRate)
    }

    @Test fun `large counters do not overflow intermediate sums`() {
        val large = session("a").copy(messageCount = Int.MAX_VALUE, inputTokens = Long.MAX_VALUE,
            cacheReadTokens = Long.MAX_VALUE, outputTokens = Long.MAX_VALUE,
            timedOutputTokens = Long.MAX_VALUE, timedDurationMs = Long.MAX_VALUE)
        val metrics = sessionMetrics(listOf(large, large.copy(id = "b")))
        assertEquals(2L * Int.MAX_VALUE, metrics.messageCount)
        assertEquals(50.0, metrics.cacheHitPercent!!, 0.0001)
        assertEquals(1000.0, metrics.tokenRate!!, 0.0001)
    }

    @Test fun `latest child time names no child and continuation retains external role`() {
        val main = session("main").copy(nativeSessionId = "same", parentSessionId = "outside")
        val continuation = main.copy(id = "continuation", parentSessionId = "", lastUsedAt = "2026-10-16T12:02:00Z")
        val child = session("child").copy(title = "Delegated work", parentSessionId = "continuation",
            startedAt = "2026-10-16T12:03:00Z", lastUsedAt = "")
        val group = groupSessions(listOf(main, continuation, child)).single()
        assertTrue(group.subagent)
        assertEquals(continuation, group.display)
        assertEquals("Sample conversation", sessionRowPresentation(group).title)
        val expectedTime = sessionRowLabels(child.copy(lastUsedAt = child.startedAt)).meta.split(" · ")[1]
        assertTrue(sessionRowPresentation(group).meta.contains(expectedTime))
        assertTrue(sessionRowPresentation(group).meta.startsWith("1 subagent · Subagent · "))
    }

    @Test fun `summary groups use weighted metrics too`() {
        val a = session("a").copy(sessionKind = "background-review", inputTokens = 100, cacheReadTokens = 900,
            outputTokens = 100, timedOutputTokens = 100, timedDurationMs = 1000)
        val b = a.copy(id = "b", inputTokens = 1900, cacheReadTokens = 100,
            outputTokens = 300, timedOutputTokens = 300, timedDurationMs = 9000)
        for (members in listOf(listOf(a, b), listOf(a, b).map { it.copy(client = "cursor", sessionKind = "", grokBotSession = true) })) {
            val row = groupSessions(members).single()
            assertEquals("Cache hit 33% · 40 tok/s", sessionRowMetricLabels(row, 0))
        }
        val single = groupSessions(listOf(a.copy(sessionKind = ""))).single()
        assertEquals(sessionMetricLabels(single.display, 0), sessionRowMetricLabels(single, 0))
    }
}
