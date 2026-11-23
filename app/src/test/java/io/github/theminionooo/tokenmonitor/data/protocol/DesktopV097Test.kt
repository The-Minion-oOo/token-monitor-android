package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.hubRuntimeLabel
import io.github.theminionooo.tokenmonitor.ui.limitQuotaCount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV097Test {
    @Test fun `saved Hub responses retain deployment and separate Notion credit pools`() {
        checkContract { name -> requireNotNull(javaClass.getResource("/protocol/v0.97.0/$name.json")).readText() }
    }

    @Test fun `released Node and Worker responses work with the Android reader`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkContract { name -> File(requireNotNull(directory), "$name.json").readText() }
    }

    private fun checkContract(read: (String) -> String) {
        val builtIn = HubProtocolParser.decodeHealth(read("health"))
        val standalone = HubProtocolParser.decodeHealth(read("v097-standalone-health"))
        assertEquals("Built-in Hub", hubRuntimeLabel(builtIn))
        assertEquals("Standalone Hub", hubRuntimeLabel(standalone))
        assertEquals("Node Hub", hubRuntimeLabel(HubProtocolParser.decodeHealth("""{"runtime":"node-hub"}""")))
        assertEquals("Node Hub", hubRuntimeLabel(standalone.copy(deployment = "future")))
        val worker = HubProtocolParser.decodeHealth(read("worker/health"))
        assertEquals("", worker.deployment)
        assertEquals(worker.runtime, hubRuntimeLabel(worker))
        for (prefix in listOf("", "worker/")) {
            val account = HubProtocolParser.decodeStats(read(prefix + "v097-notion")).limits.providers.single()
            val (standard, select) = account.windows
            assertEquals(listOf("Standard", "Select"), account.windows.map { it.label })
            assertEquals(listOf(1500.0, 500.0), account.windows.map { it.limit })
            assertEquals(listOf(134.5, 0.0), account.windows.map { it.used })
            assertEquals(listOf(1365.5, 500.0), account.windows.map { it.remaining })
            assertEquals(standard.resetsAt, select.resetsAt)
            assertNotNull(limitQuotaCount(account, standard, showUsed = false))
            assertNotNull(limitQuotaCount(account, select, showUsed = true))
        }
    }
}
