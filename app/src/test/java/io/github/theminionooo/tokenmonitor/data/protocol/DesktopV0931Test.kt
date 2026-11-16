package io.github.theminionooo.tokenmonitor.data.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV0931Test {
    @Test fun `saved Hub responses show one project for a repository and its worktree`() {
        checkProjects { name -> requireNotNull(javaClass.getResource("/protocol/v0.93.1/$name.json")).readText() }
    }

    @Test fun `released Hubs preserve the desktop project rollup`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkProjects { name -> File(requireNotNull(directory), "$name.json").readText() }
    }

    private fun checkProjects(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val today = HubProtocolParser.decodeStats(read(prefix + "v0931-projects")).periods.getValue("today")
            val project = today.projects.single()
            assertEquals("Example App", project.label)
            assertEquals(100L, project.totalTokens)
            assertEquals(mapOf("codex" to 60L, "claude" to 40L), project.clients)
            assertEquals(
                mapOf("main-demo" to project.label, "worktree-demo" to project.label),
                today.sessions.associate { it.id to it.projectLabel },
            )
        }
    }
}
