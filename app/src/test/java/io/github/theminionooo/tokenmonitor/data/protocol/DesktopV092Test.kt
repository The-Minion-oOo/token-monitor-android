package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitSpendRows
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV092Test {
    @Test fun `saved fixtures keep Copilot accounts and DeepSeek fees distinct`() {
        checkLimits { name -> javaClass.getResource("/protocol/v0.92.0/$name.json")!!.readText() }
    }

    @Test fun `released Hub limits and Worker recovery reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkLimits { name -> File(directory!!, "$name.json").readText() }
        DesktopV081Test().verifyChain(File(directory!!, "v092-worker-recovery.json").readText())
    }

    @Test fun `missing or invalid fees keep only the DeepSeek balance`() {
        for (fees in listOf("", ""","searchSpend":-1,"fetchSpend":"NaN"""")) {
            val raw = """{"limits":{"providers":[{"provider":"deepseek","balance":{"currency":"USD"$fees},
                "windows":[{"label":"Balance","currency":"USD","metric":"credits","remaining":10}]}]}}"""
            val account = HubProtocolParser.decodeStats(raw).limits.providers.single()
            assertNull(account.spend)
            assertEquals(10.0, account.windows.single().remaining)
        }
    }

    private fun checkLimits(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val accounts = HubProtocolParser.decodeStats(read(prefix + "v092-limits")).limits.providers
            val copilot = accounts.filter { it.provider == "copilot" }.associateBy { it.accountName }
            assertEquals(setOf("Demo Personal", "Demo Work"), copilot.keys)
            assertEquals(2, copilot.values.map(::limitAccountIdentity).distinct().size)
            assertEquals(75.0, copilot.getValue("Demo Personal").windows.single().remainingPercent)
            val work = copilot.getValue("Demo Work")
            assertEquals("unauthorized", work.status)
            assertTrue(work.windows.isEmpty())

            val deepseek = accounts.single { it.provider == "deepseek" }
            assertEquals(10.0, deepseek.windows.single().remaining)
            val spend = requireNotNull(deepseek.spend)
            assertEquals("CNY", spend.currency)
            assertEquals(listOf("Search fees this month" to 3.0, "Fetch fees this month" to 0.02), limitSpendRows(spend))
        }
    }
}
