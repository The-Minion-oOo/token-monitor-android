package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitDisplayProvider
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.sessionContextForRow
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV0951Test {
    @Test fun `saved Hub fixtures keep the new tools and quota rows separate`() {
        for (prefix in listOf("", "worker/")) {
            val resource = "/protocol/v0.95.1/${prefix}v0951-compatibility.json"
            checkSnapshot(requireNotNull(javaClass.getResource(resource)).readText())
        }
    }

    @Test fun `released Node and Worker supply the same Android fields`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        for (prefix in listOf("", "worker/")) {
            checkSnapshot(File(requireNotNull(directory), "${prefix}v0951-compatibility.json").readText())
        }
    }

    private fun checkSnapshot(raw: String) {
        val stats = HubProtocolParser.decodeStats(raw)
        val today = stats.periods.getValue("today")
        assertEquals(70L, today.clients["junie"])
        assertEquals(60L, today.clients["opencodereview"])
        val sessions = today.sessions.associateBy { it.id }
        assertEquals("Junie", sessions.getValue("junie-demo").client.displayName())
        assertEquals("OpenCodeReview", sessions.getValue("review-demo").client.displayName())
        for ((id, left) in mapOf("grok-demo" to 70, "workbuddy-demo" to 60, "catpaw-demo" to 50)) {
            val session = sessions.getValue(id)
            val now = Instant.parse(session.lastUsedAt).toEpochMilli()
            assertEquals(left, sessionContextForRow(session, now)?.percentLeft)
        }

        val accounts = stats.limits.providers.filter { it.sourceDeviceId.startsWith("synthetic-v0951-") }
        val qoder = accounts.filter { it.provider == "qoder" }
        assertEquals(3, qoder.size)
        assertEquals(3, qoder.map(::limitAccountIdentity).distinct().size)
        assertEquals(setOf("Qoder", "Qoder CN"), qoder.map { limitDisplayProvider(it).providerLabel() }.toSet())
        assertEquals(setOf(25.0, 35.0, 45.0), qoder.map { it.windows.single().usedPercent }.toSet())
        val global = qoder.first { it.region == "global" }
        assertEquals("qoder", limitDisplayProvider(global.copy(region = "")))

        val team = accounts.filter { it.provider == "zaiteam" }
        assertEquals(2, team.size)
        assertEquals(mapOf("demo-project-a" to 30.0, "demo-project-b" to 40.0),
            team.associate { it.accountKey to it.windows.single().usedPercent })
        assertTrue(team[0].accountKey != team[1].accountKey)
    }
}
