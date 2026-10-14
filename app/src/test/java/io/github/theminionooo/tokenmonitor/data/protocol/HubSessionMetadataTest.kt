package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.LiveActivity
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop v0.69.0 session identity, relationship, activity and per-model fields. */
class HubSessionMetadataTest {
    private val parentNative = "0b8f6c2e-3d41-4a6b-9c0e-5a1d2f7e8b01"

    @Test fun `v0 69 fixture maps session relationships activity and models on every surface`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            resource("health.json"), resource("stats.json"), resource("devices.json"),
            resource("history.json"), resource("subscriptions.json"), 0,
        )
        assertEquals("v0.69.0", snapshot.health.hubBuild)
        assertEquals(19_100L, snapshot.today.totalTokens)
        assertEquals(snapshot.today.totalTokens, snapshot.today.sessions.sumOf { it.totalTokens })
        assertEquals(12, snapshot.today.sessions.size)
        assertEquals(snapshot.today.sessions, snapshot.stats.periods.getValue("month").sessions)
        assertEquals(snapshot.today.sessions, snapshot.stats.devices.single().periods.getValue("today").sessions)
        assertTrue(snapshot.stats.periods.getValue("allTime").sessions.isEmpty())
        assertEquals(19_100L, snapshot.history.daily.single().tokens)

        val sessions = snapshot.today.sessions.associateBy { it.id }
        val parent = sessions.getValue("sample-codex-parent")
        assertEquals(parentNative, parent.nativeSessionId)
        assertEquals(false, parent.waitingForInput)
        assertEquals("", parent.parentSessionId)
        assertEquals(mapOf("gpt-5" to 6_000L), parent.modelTokens)
        assertEquals(listOf("gpt-5"), parent.modelNames)

        val subagent = sessions.getValue("sample-codex-subagent")
        assertEquals(parent.id, subagent.parentSessionId)
        assertEquals("", subagent.parentClient)
        assertEquals("codex", subagent.client)
        assertEquals("5e7a91d4-2c3b-4f08-8a6d-1b9c0e4f7a22", subagent.nativeSessionId)

        assertEquals(parentNative, sessions.getValue("sample-codex-continuation").nativeSessionId)
        assertEquals("", sessions.getValue("sample-codex-continuation").parentSessionId)

        val review = sessions.getValue("sample-codex-review")
        assertEquals("background-review", review.sessionKind)
        assertEquals(parent.id, review.parentSessionId)

        assertEquals(true, sessions.getValue("sample-codex-waiting").waitingForInput)

        val t3Claude = sessions.getValue("sample-t3-claude")
        val t3Codex = sessions.getValue("sample-t3-codex")
        assertEquals("sample-t3-thread-1", t3Claude.t3ThreadId)
        assertEquals(t3Claude.t3ThreadId, t3Codex.t3ThreadId)
        assertEquals("sample-t3-claude", t3Codex.parentSessionId)
        assertEquals("claude", t3Codex.parentClient)

        val live = sessions.getValue("sample-claude-live")
        assertEquals(LiveActivity("waiting", "2026-10-11T11:59:50.000Z"), live.liveActivity)
        assertNull(live.waitingForInput)

        val sand = sessions.getValue("sand-subagent-sample-1")
        assertEquals("cursor", sand.client)
        assertEquals(mapOf("claude-sonnet-4-5" to 700L), sand.modelTokens)
        val bot = sessions.getValue("sample-cursor-grok-bot")
        assertEquals(mapOf("grok-bot-1" to 500L, "claude-sonnet-4-5" to 200L), bot.modelTokens)
        assertNull(bot.grokBotSession)
        assertEquals(mapOf("claude-sonnet-4-5" to 400L), sessions.getValue("sample-cursor-plain").modelTokens)

        val dots = sessions.getValue("dots-example")
        assertTrue(dots.dotsObservedOnly)
        assertEquals(400L, dots.unpricedTokens)
    }

    @Test fun `v0 69 stream and freshness retain the new session fields`() {
        val source = resource("stats.json")
        val expected = HubProtocolParser.decodeStats(source)
        fun event(name: String) = resource(name).lineSequence()
            .first { it.startsWith("data:") }.removePrefix("data:").trim()
        assertEquals(expected, HubProtocolParser.decodeStatsStreamEvent(event("stats-stream.sse")))
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(source, event("stats-freshness.sse")))
        assertEquals("2026-10-11T12:02:00.000Z", refreshed.updatedAt)
        assertEquals(expected.periods, refreshed.periods)
        val live = refreshed.periods.getValue("today").sessions.first { it.id == "sample-claude-live" }
        assertEquals("waiting", live.liveActivity?.state)
    }

    @Test fun `earlier fixtures keep the new session fields at their defaults`() {
        val versions = listOf(
            "v0.54.0", "v0.55.0", "v0.56.0", "v0.60.0", "v0.61.0", "v0.62.0", "v0.63.0", "v0.63.1",
            "v0.64.0", "v0.65.0", "v0.66.0", "v0.67.0", "v0.68.0",
        )
        var checked = 0
        for (version in versions) {
            val periods = HubProtocolParser.decodeStats(resource("stats.json", version)).periods
            for (session in periods.values.flatMap { it.sessions }) {
                checked++
                assertEquals("$version ${session.id}", "", session.nativeSessionId + session.t3ThreadId + session.parentSessionId + session.parentClient)
                assertNull("$version ${session.id}", session.liveActivity)
                assertNull("$version ${session.id}", session.waitingForInput)
                assertNull("$version ${session.id}", session.grokBotSession)
            }
        }
        assertTrue("Expected earlier fixtures to contain sessions", checked > 0)
        val dots = HubProtocolParser.decodeStats(resource("stats.json", "v0.68.0")).periods.getValue("today").sessions.single()
        assertEquals(mapOf("unpriced-model" to 400L), dots.modelTokens)
        assertTrue(dots.dotsObservedOnly)
    }

    @Test fun `live activity needs a known state and a non blank observation time`() {
        fun activity(json: String): LiveActivity? = session("""{"liveActivity":$json}""").liveActivity
        for (state in listOf("running", "waiting", "idle", "unknown")) {
            assertEquals(LiveActivity(state, "2026-10-11T12:00:00.000Z"),
                activity("""{"state":"$state","observedAt":"2026-10-11T12:00:00.000Z"}"""))
        }
        assertEquals(LiveActivity("idle", "2026-10-11T12:00:00.000Z"),
            activity("""{"state":"idle","observedAt":" 2026-10-11T12:00:00.000Z ","prompt":"ignored"}"""))
        assertNull(activity("""{"state":"busy","observedAt":"2026-10-11T12:00:00.000Z"}"""))
        assertNull(activity("""{"state":"Running","observedAt":"2026-10-11T12:00:00.000Z"}"""))
        assertNull(activity("""{"state":"","observedAt":"2026-10-11T12:00:00.000Z"}"""))
        assertNull(activity("""{"observedAt":"2026-10-11T12:00:00.000Z"}"""))
        assertNull(activity("""{"state":"running"}"""))
        assertNull(activity("""{"state":"running","observedAt":"   "}"""))
        assertNull(activity("""{"state":"running","observedAt":null}"""))
        assertNull(activity("""{"state":"running","observedAt":1760184000000}"""))
        assertNull(activity("\"running\""))
        assertNull(activity("null"))
        assertNull(activity("[]"))
        assertNull(session("{}").liveActivity)
    }

    @Test fun `question waiting and the Grok Bot marker must be JSON booleans`() {
        fun flags(json: String) = session(json).let { it.waitingForInput to it.grokBotSession }
        assertEquals(true to false, flags("""{"waitingForInput":true,"grokBotSession":false}"""))
        assertEquals(false to true, flags("""{"waitingForInput":false,"grokBotSession":true}"""))
        assertEquals(null to null, flags("{}"))
        assertEquals(null to null, flags("""{"waitingForInput":"true","grokBotSession":"false"}"""))
        assertEquals(null to null, flags("""{"waitingForInput":1,"grokBotSession":0}"""))
        assertEquals(null to null, flags("""{"waitingForInput":null,"grokBotSession":null}"""))
        assertEquals(null to null, flags("""{"waitingForInput":{},"grokBotSession":[]}"""))
    }

    @Test fun `per model tokens keep only finite positive counts and model names stay complete`() {
        val parsed = session("""{"models":{"a":5,"b":0,"c":-3,"d":"x","e":1e999,"f":12.0,"g":null,"h":"7","i":0.4}}""")
        assertEquals(mapOf("a" to 5L, "f" to 12L), parsed.modelTokens)
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"), parsed.modelNames)
        assertTrue(session("""{"models":[1,2]}""").modelTokens.isEmpty())
        assertTrue(session("""{"models":"gpt-5"}""").modelTokens.isEmpty())
        assertTrue(session("{}").modelTokens.isEmpty())
    }

    @Test fun `identity and relationship strings are trimmed and blank when absent`() {
        val linked = session(
            """{"nativeSessionId":" $parentNative ","t3ThreadId":" thread-1 ","parentSessionId":" parent-1 ","parentClient":" claude "}""",
        )
        assertEquals(parentNative, linked.nativeSessionId)
        assertEquals("thread-1", linked.t3ThreadId)
        assertEquals("parent-1", linked.parentSessionId)
        assertEquals("claude", linked.parentClient)
        val blank = session("""{"nativeSessionId":"   ","t3ThreadId":null,"parentSessionId":"","parentClient":null}""")
        assertEquals("", blank.nativeSessionId + blank.t3ThreadId + blank.parentSessionId + blank.parentClient)
        val absent = session("{}")
        assertEquals("", absent.nativeSessionId + absent.t3ThreadId + absent.parentSessionId + absent.parentClient)
        assertFalse(absent.dotsObservedOnly)
    }

    @Test fun `unknown future session fields are ignored beside the v0 69 fields`() {
        val parsed = session("""{"nativeSessionId":"$parentNative","futureField":{"nested":[1,2,3]},"liveActivity":{"state":"running","observedAt":"2026-10-11T12:00:00.000Z","extra":true}}""")
        assertEquals(parentNative, parsed.nativeSessionId)
        assertEquals("running", parsed.liveActivity?.state)
    }

    private fun session(json: String): SessionUsage = HubProtocolParser.decodeStats(
        """{"periods":{"today":{"sessions":{"codex:sample":$json}}}}""",
    ).periods.getValue("today").sessions.single()

    private fun resource(name: String, version: String = "v0.69.0"): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/$version/$name"),
    ).bufferedReader().use { it.readText() }
}
