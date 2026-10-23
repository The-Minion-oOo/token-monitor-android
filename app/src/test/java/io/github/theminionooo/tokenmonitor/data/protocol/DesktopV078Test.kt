package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.groupSessions
import io.github.theminionooo.tokenmonitor.ui.sessionRowPresentation
import org.junit.Assert.*
import org.junit.Test

class DesktopV078Test {
    private fun response(name: String) = javaClass.getResource("/protocol/v0.78.0/$name")!!.readText()
    private fun sessions(name: String) = HubProtocolParser.decodeStats(response("$name.json")).periods.getValue("today").sessions

    @Test fun `released endpoints and freshness retain complete usage`() {
        val snapshot = HubProtocolParser.decodeSnapshot(response("health.json"), response("stats.json"), response("devices.json"),
            response("history.json"), response("subscriptions.json"), 0)
        assertTrue(snapshot.health.ok)
        assertEquals(1234L, snapshot.today.totalTokens)
        assertEquals(1234L, snapshot.history.daily.single().tokens)
        assertEquals(1234L, snapshot.stats.devices.single().periods.getValue("today").totalTokens)
        fun frame(file: String) = response(file).lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
        val full = HubStreamProtocol.normalizeComplete(frame("stats-stream.sse"))
        val fresh = HubStreamProtocol.mergeFreshness(full, frame("stats-freshness.sse"))
        assertEquals(snapshot.stats.periods, HubProtocolParser.decodeStats(fresh).periods)
    }

    @Test fun `ZCode tool and workflow children fold under their titled parent`() {
        val rows = groupSessions(sessions("zcode-titles"))
        val parent = rows.single { it.grouped }
        assertEquals("sample-zcode-parent", parent.display.id)
        assertEquals(1500L, parent.totalTokens)
        assertEquals(2, parent.subagentCount)
        assertEquals(3, parent.members.size)
        assertEquals("Plan the sample release", sessionRowPresentation(parent).title)
        assertEquals(2400L, rows.sumOf { it.totalTokens })
    }

    @Test fun `ZCode deliberate forks and side chats keep their own rows`() {
        val rows = groupSessions(sessions("zcode-titles"))
        for (id in listOf("sample-zcode-fork", "sample-zcode-side")) {
            val row = rows.single { it.display.id == id }
            assertFalse(row.grouped)
            assertFalse(row.subagent)
            assertTrue(row.display.parentSessionId.isBlank())
        }
        assertEquals(3, rows.size)
    }

    @Test fun `title consent and revocation never change usage or relationships`() {
        val shared = sessions("zcode-titles")
        assertTrue(shared.all { it.title.isNotBlank() })
        for (file in listOf("zcode-private", "zcode-revoked")) {
            val hidden = sessions(file)
            assertTrue(hidden.all { it.title.isBlank() })
            assertEquals(shared.map { it.copy(title = "") }, hidden)
            val rows = groupSessions(hidden)
            assertEquals(1500L, rows.single { it.grouped }.totalTokens)
            assertFalse(sessionRowPresentation(rows.single { it.grouped }).title.contains("Plan the sample"))
        }
    }

    @Test fun `local title preference hides a shared group title`() {
        val parent = groupSessions(sessions("zcode-titles")).single { it.grouped }
        assertEquals("Plan the sample release", sessionRowPresentation(parent, showTitles = true).title)
        assertFalse(sessionRowPresentation(parent, showTitles = false).title.contains("Plan the sample"))
        assertTrue(sessionRowPresentation(parent, showTitles = false).title.contains("ZCode"))
    }

    @Test fun `missing parent stays visible as a standalone subagent`() {
        val child = sessions("zcode-titles").single { it.id == "sample-zcode-child" }
        val row = groupSessions(listOf(child)).single()
        assertFalse(row.grouped)
        assertTrue(row.subagent)
        assertEquals(200L, row.totalTokens)
        assertEquals(child.title, sessionRowPresentation(row).title)
    }
}
