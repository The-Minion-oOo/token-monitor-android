package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.network.HubStreamEvent
import io.github.theminionooo.tokenmonitor.ui.displayName
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV093Test {
    @Test fun `saved Hub responses attribute Qoder editions separately`() {
        checkUsage { name -> javaClass.getResource("/protocol/v0.93.0/$name.json")!!.readText() }
    }

    @Test fun `released Hub responses preserve Qoder usage and restart the stream`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        fun read(name: String) = File(directory!!, "$name.json").readText()
        checkUsage(::read)
        val replacement = StatsStreamReceiver().receive(HubStreamEvent("snapshot", read("v093-replacement")))
        assertEquals("snapshot", replacement?.type)
        assertEquals(93L, HubProtocolParser.decodeStats(requireNotNull(replacement?.completeStats))
            .periods.getValue("today").totalTokens)
    }

    private fun checkUsage(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val stats = HubProtocolParser.decodeStats(read(prefix + "v093-qoder"))
            val today = stats.periods.getValue("today")
            assertEquals(130L, today.totalTokens)
            assertEquals(mapOf("qoder" to 90L, "qodercn" to 40L), today.clients)
            assertEquals(mapOf("demo-global" to "Qoder", "demo-cn" to "Qoder CN"),
                today.sessions.associate { it.id to it.client.displayName() })
            val quota = stats.limits.providers.single { it.provider == "qoder" }
            assertTrue(quota.accountKey.startsWith("sha256:"))
            assertEquals(75.0, quota.windows.single().remainingPercent)
        }
    }
}
