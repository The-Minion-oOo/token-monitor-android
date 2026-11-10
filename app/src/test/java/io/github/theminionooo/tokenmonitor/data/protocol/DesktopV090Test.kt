package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.harkQuotaCount
import io.github.theminionooo.tokenmonitor.ui.limitReportedAmount
import io.github.theminionooo.tokenmonitor.ui.vendorOf
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV090Test {
    @Test fun `saved fixtures preserve quota windows and lifetime count`() {
        checkRelease { name -> javaClass.getResource("/protocol/v0.90.0/$name.json")!!.readText() }
    }

    @Test fun `released Node and Worker Hub reads reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkRelease { name -> File(directory!!, "$name.json").readText() }
    }

    @Test fun `only a complete nonnegative integer count is trusted`() {
        val invalid = listOf(
            """"activeDays": 7""",
            """"activeDays": 7, "activeDaysComplete": "true"""",
            """"activeDays": "7", "activeDaysComplete": true""",
            """"activeDays": 7.5, "activeDaysComplete": true""",
            """"activeDays": -1, "activeDaysComplete": true""",
            """"activeDays": 2147483648, "activeDaysComplete": true""",
        )
        invalid.forEach { fields ->
            assertNull(fields, HubProtocolParser.decodeHistory("""{"summary":{$fields}}""").lifetimeActiveDays)
        }
        assertEquals(0, HubProtocolParser.decodeHistory(
            """{"summary":{"activeDays":0,"activeDaysComplete":true}}"""
        ).lifetimeActiveDays)
    }

    private fun checkRelease(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = read(prefix + name)
            val fullHistory = response("v090-complete-history")
            fun snapshot(name: String) = HubProtocolParser.decodeSnapshot(
                response("health"), response(name), null, fullHistory, null, 0,
            )
            val complete = snapshot("v090-complete")
            val mixed = snapshot("v090-mixed")
            assertEquals(2, HubProtocolParser.decodeHistory(fullHistory).lifetimeActiveDays)
            assertEquals(2, complete.history.lifetimeActiveDays)
            assertEquals(1, complete.history.daily.size)
            assertNull(mixed.history.lifetimeActiveDays)
            assertNull(HubProtocolParser.decodeHistory(response("v090-mixed-history")).lifetimeActiveDays)
            assertNull(snapshot("stats").history.lifetimeActiveDays)

            val hark = complete.stats.limits.providers.single { it.provider == "hark" }
            val (daily, monthly, topUp) = hark.windows
            assertEquals(listOf("Daily", "Monthly", "Top-up"), hark.windows.map { it.label })
            assertEquals("20.0K / 100.0K", harkQuotaCount(hark, daily, showUsed = false))
            assertEquals("80.0K / 100.0K", harkQuotaCount(hark, daily, showUsed = true))
            assertEquals("500.0K / 1.0M", harkQuotaCount(hark, monthly, showUsed = false))
            assertNull(harkQuotaCount(hark, topUp, showUsed = false))
            assertEquals(1.25, limitReportedAmount(hark.provider, topUp))
            assertEquals("USD", topUp.currency)
            assertEquals(false, topUp.showMeter)

            val qoder = complete.stats.limits.providers.single { it.provider == "qoder" }.windows
            assertEquals(listOf("Credits", "Team credits"), qoder.map { it.label })
            assertEquals(listOf(75.0, 80.0), qoder.map { it.remainingPercent })
            assertEquals(2, qoder.map { it.resetsAt }.filter { it.isNotBlank() }.distinct().size)
            assertEquals(listOf("Credits"), mixed.stats.limits.providers.single { it.provider == "qoder" }.windows.map { it.label })
            assertEquals("thinkingmachines", vendorOf("inkling-test"))
            assertEquals("thinkingmachines", vendorOf("Thinking Machines"))
            assertEquals("hark", vendorOf(hark.provider))
        }
    }
}
