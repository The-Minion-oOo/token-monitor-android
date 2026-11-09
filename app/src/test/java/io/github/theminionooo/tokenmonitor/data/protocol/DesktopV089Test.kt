package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.ModelAliasGrouping
import io.github.theminionooo.tokenmonitor.domain.ModelAliasSettings
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.groupSessions
import io.github.theminionooo.tokenmonitor.ui.matchesSearch
import io.github.theminionooo.tokenmonitor.ui.sessionRowPresentation
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV089Test {
    @Test fun `saved fixtures preserve model observations and Gemini CLI usage`() {
        checkRelease { name -> javaClass.getResource("/protocol/v0.89.0/$name.json")!!.readText() }
    }

    @Test fun `released Node and Worker Hub reads reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkRelease { name -> File(directory!!, "$name.json").readText() }
    }

    @Test fun `malformed latest model cannot replace the model fallback`() {
        for (value in listOf("null", "42", "{}", "\"\"", "\"\\u0007model\"", "\"" + "x".repeat(257) + "\"")) {
            val stats = HubProtocolParser.decodeStats(
                """{"periods":{"today":{"sessions":{"s":{"client":"codex","latestModel":$value,"models":{"gpt-5":10}}}}}}"""
            )
            val session = stats.periods.getValue("today").sessions.single()
            assertNull(session.latestModel)
            assertEquals("Codex · gpt-5", sessionRowPresentation(groupSessions(listOf(session)).single()).title)
        }
    }

    private fun checkRelease(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = read(prefix + name)
            val base = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"),
                response("history"), response("subscriptions"), 0)
            assertTrue(base.health.ok)
            assertEquals(1234L, base.today.totalTokens)
            val snapshot = base.copy(stats = HubProtocolParser.decodeStats(response("v089-stats")))
            val today = snapshot.today
            val gemini = today.sessions.single { it.client == "gemini" }
            assertEquals("Gemini CLI", gemini.client.displayName())
            assertEquals(260L, today.totalTokens)
            assertEquals(35L, today.clients.getValue("gemini"))
            assertEquals(25L, gemini.outputTokens)
            assertNull(gemini.latestModel)
            assertNull(today.sessions.single { it.id == "unknown" }.latestModel)
            val archived = today.sessions.single { it.client == "claude" }
            assertTrue(archived.archived)
            assertEquals("claude-opus-4-7", archived.latestModel)
            val row = groupSessions(today.sessions).single { it.display.id == "continuation" }
            assertEquals("openai/gpt-5.5", row.display.latestModel)
            assertEquals(120L, row.totalTokens)
            assertEquals(mapOf("gpt-5" to 100L, "gpt-5-mini" to 20L), row.modelTokens)
            val names = ModelAliasSettings(1, emptyMap(), ModelAliasGrouping.Prefix).resolver(snapshot)
            assertEquals("Codex · gpt-5.5 +2", sessionRowPresentation(row, names = names).title)
            assertTrue(row.display.matchesSearch("openai/gpt-5.5", showTitles = false, names = names))
        }
    }
}
