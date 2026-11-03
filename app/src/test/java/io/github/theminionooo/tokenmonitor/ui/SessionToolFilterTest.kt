package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.*
import org.junit.Test

class SessionToolFilterTest {
    private fun sessions() = HubProtocolParser.decodeStats(javaClass.getResource("/protocol/v0.85.0/session-tools.json")!!.readText()).periods.getValue("today").sessions

    @Test fun `tool choices are distinct stable identifiers and ignore blanks`() {
        val rows = sessions()
        assertEquals(listOf("claude", "codex", "copilot", "senpi"), sessionToolIds(rows + rows.first().copy(client = "")))
    }
    @Test fun `a removed tool or single tool clears the selection`() {
        assertEquals("", sessionToolSelection("claude", listOf("codex")))
        assertEquals("", sessionToolSelection("missing", listOf("codex", "claude")))
        assertEquals("claude", sessionToolSelection("claude", listOf("codex", "claude")))
    }
    @Test fun `a matching child keeps the entire mixed tool conversation and totals`() {
        val rows = groupSessions(sessions())
        val filtered = filterSessionRows(rows, "claude")
        assertEquals(2, filtered.size)
        assertEquals(200L, filtered.sumOf { it.totalTokens })
        assertEquals(140L, filtered.single { it.grouped }.totalTokens)
        assertEquals(250L, filterSessionRows(rows, "").sumOf { it.totalTokens })
        assertEquals(250L, sessions().sumOf { it.totalTokens })
    }
    @Test fun `tool and text search keep a matching member with its group`() {
        val rows = filterSessionRows(groupSessions(sessions()), "codex")
        val result = searchSessionRows(rows, true, { it.title.contains("Write tests") }, "Write tests")
        assertEquals(140L, result.single().totalTokens)
    }
    @Test fun `hidden group titles cannot affect search`() {
        val result = searchSessionRows(groupSessions(sessions()), true, { false }, "Release plan", showTitles = false)
        assertTrue(result.isEmpty())
    }
    @Test fun `choice memoization uses list identity without comparing records`() {
        val rows = sessions()
        assertEquals(SessionToolInputs(rows), SessionToolInputs(rows))
        assertNotEquals(SessionToolInputs(rows), SessionToolInputs(rows.toMutableList()))
    }
}
