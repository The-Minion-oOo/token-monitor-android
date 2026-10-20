package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import org.junit.Assert.*
import org.junit.Test

class ProjectBreakdownTest {
    private val project = ProjectUsage("sample", "Sample", 100, 0.0, 0, mapOf("codex" to 100))

    @Test fun `invalid or excessive model attribution cannot exceed project total`() {
        val shares = projectShares(project.copy(models = mapOf("" to 10, "a" to -1, "b" to Long.MAX_VALUE, "c" to 99)), true)
        assertEquals(listOf(ProjectShare("b", 100)), shares)
        assertTrue(projectShares(project.copy(totalTokens = 0), true).isEmpty())
        assertTrue(projectShares(project.copy(totalTokens = -1), false).isEmpty())
    }

    @Test fun `older project tool attribution stays intact and models are unknown`() {
        assertEquals(listOf(ProjectShare("codex", 100)), projectShares(project, false))
        assertEquals(listOf(ProjectShare("Unknown model", 100, true)), projectShares(project, true))
    }
}
