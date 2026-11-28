package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.sessionContextForRow
import io.github.theminionooo.tokenmonitor.ui.sessionModelMetadata
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV100Test {
    @Test fun `saved Hub responses keep context separate from usage`() {
        checkResponses { path ->
            requireNotNull(javaClass.getResource("/protocol/v0.100.0/$path.json")).readText()
        }
    }

    @Test fun `released Node and Worker responses use existing Android session fields`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkResponses { path -> File(requireNotNull(directory), "$path.json").readText() }
    }

    private fun checkResponses(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val today = HubProtocolParser.decodeStats(read(prefix + "v100-session")).periods.getValue("today")
            val measured = today.sessions.single { it.contextWindow > 0 }
            val now = Instant.parse(measured.lastUsedAt).toEpochMilli()
            assertEquals("antigravity", measured.client)
            assertEquals(90L, measured.totalTokens)
            assertEquals(75, sessionContextForRow(measured, now)?.percentLeft)
            assertNull(sessionContextForRow(measured, now + 31 * 60_000L))
            assertNull(sessionContextForRow(today.sessions.single {
                it.client == "antigravity" && it.contextWindow == 0L
            }, now))

            val cleared = HubProtocolParser.decodeStats(read(prefix + "v100-cleared-context"))
                .periods.getValue("today").sessions.single()
            assertEquals(measured.id, cleared.id)
            assertEquals(measured.totalTokens, cleared.totalTokens)
            assertNull(sessionContextForRow(cleared, now))

            val work = today.sessions.single { it.client == "codex" }
            assertEquals("gpt-5.6-sol", sessionModelMetadata(work))
            assertTrue(work.dotsObservedOnly)
            assertEquals(31L, today.sessions.single { it.client == "amp" }.totalTokens)
        }
    }
}
