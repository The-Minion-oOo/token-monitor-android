package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.groupSessions
import io.github.theminionooo.tokenmonitor.ui.sessionRowPresentation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV098Test {
    @Test fun `saved Node and Worker stats keep untitled sessions and ignore range revisions`() {
        checkSession { name -> requireNotNull(javaClass.getResource("/protocol/v0.98.0/$name.json")).readText() }
    }

    @Test fun `released Node and Worker stats keep untitled sessions and ignore range revisions`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkSession { name -> File(requireNotNull(directory), "$name.json").readText() }
    }

    private fun checkSession(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val stats = HubProtocolParser.decodeStats(read(prefix + "v098-session"))
            val session = stats.periods.getValue("today").sessions.single()
            assertEquals("antigravity", session.client)
            assertEquals(120L, session.totalTokens)
            assertTrue(session.title.isBlank())
            val row = groupSessions(listOf(session)).single()
            assertEquals("Sample release", sessionRowPresentation(row).title)
            assertTrue(sessionRowPresentation(row).meta.contains("Antigravity"))
            assertTrue(sessionRowPresentation(row, showTitles = false).title.startsWith("Antigravity"))
            val titled = groupSessions(listOf(session.copy(title = "Sample conversation"))).single()
            assertEquals("Sample conversation", sessionRowPresentation(titled).title)
            val noProject = groupSessions(listOf(session.copy(projectLabel = ""))).single()
            assertTrue(sessionRowPresentation(noProject).title.startsWith("Antigravity"))
        }
    }
}
