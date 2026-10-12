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
    }
}
