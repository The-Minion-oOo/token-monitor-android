package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.SessionActivityState
import io.github.theminionooo.tokenmonitor.ui.groupSessions
import io.github.theminionooo.tokenmonitor.ui.sessionActivityState
import io.github.theminionooo.tokenmonitor.ui.sessionRowActivity
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV099Test {
    @Test fun `saved compact Hub responses preserve activity and history`() {
        checkResponses { name -> requireNotNull(javaClass.getResource("/protocol/v0.99.0/$name.json")).readText() }
    }

    @Test fun `released Node and Worker retain activity and history for the Android reader`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkResponses { name -> File(requireNotNull(directory), "$name.json").readText() }
    }

    private fun checkResponses(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val stats = HubProtocolParser.decodeStats(read(prefix + "v099-session"))
            val sessions = stats.periods.getValue("today").sessions
            val antigravity = sessions.single { it.client == "antigravity" }
            val observation = requireNotNull(antigravity.liveActivity)
            val now = Instant.parse(observation.observedAt).toEpochMilli()
            assertEquals(false, antigravity.turnEnded)
            assertEquals(SessionActivityState.Running, sessionActivityState(antigravity, now))
            assertEquals(SessionActivityState.Idle, sessionActivityState(antigravity, now + 30_000))
            assertEquals(SessionActivityState.Idle, sessionActivityState(antigravity.copy(archived = true), now))
            assertEquals(SessionActivityState.Idle, sessionActivityState(antigravity.copy(liveActivity = null), now))
            assertEquals(SessionActivityState.Idle, sessionActivityState(antigravity.copy(liveActivity = observation.copy(state = "idle")), now))

            val group = groupSessions(sessions).single()
            assertEquals(setOf("antigravity", "kiro"), group.sessions.map { it.client }.toSet())
            assertEquals(120L, group.totalTokens)
            assertEquals(SessionActivityState.Running, sessionRowActivity(group, now))
            val device = HubProtocolParser.decodeDevices(read(prefix + "v099-devices")).single()
            assertEquals(125L, device.periods.getValue("today").totalTokens)
            assertEquals(1, device.history.lifetimeActiveDays)
            assertTrue(device.history.daily.any { it.tokens == 17L })
        }
    }
}
