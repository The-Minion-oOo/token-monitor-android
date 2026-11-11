package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.storage.usageCurrencies
import io.github.theminionooo.tokenmonitor.ui.formatSubscriptionAmount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV091Test {
    @Test fun `saved fixtures preserve subscription amounts and currencies`() {
        checkSubscriptions { name -> javaClass.getResource("/protocol/v0.91.0/$name.json")!!.readText() }
    }

    @Test fun `released Node and Worker subscription currencies reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkSubscriptions { name -> File(directory!!, "$name.json").readText() }
    }

    private fun checkSubscriptions(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            val rows = HubProtocolParser.decodeSubscriptions(read(prefix + "v091-subscriptions")).entries
            val recurring = rows.filter { it.kind == "subscription" }.associateBy { it.currency }
            assertEquals(usageCurrencies.toSet(), recurring.keys)
            for ((code, row) in recurring) {
                val wholeUnits = code == "JPY" || code == "KRW"
                assertEquals(if (wholeUnits) 1_234_500L else 1234L, row.amountMinor)
                val expected = when {
                    code == "USD" -> "$12.34"
                    wholeUnits -> "$code 12,345"
                    else -> "$code 12.34"
                }
                assertEquals(expected, formatSubscriptionAmount(row.amountMinor, code))
            }
            val topUp = rows.single { it.kind == "topup" }
            assertEquals("KRW 4,567", formatSubscriptionAmount(topUp.topUps.single().amountMinor, topUp.currency))
        }
    }
}
