package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.formatBoundary
import io.github.theminionooo.tokenmonitor.ui.quotaPercentLeft
import org.junit.Assert.*
import org.junit.Test

class DesktopV0841Test {
    private fun accounts(name: String) = listOf("", "worker/").map { prefix ->
        HubProtocolParser.decodeStats(javaClass.getResource("/protocol/v0.84.1/$prefix$name.json")!!.readText()).limits.providers
    }

    @Test fun `Kiro restored and legacy fallback readings preserve credits and reset`() {
        for (name in listOf("kiro-v2", "kiro-legacy-flag", "kiro-summary-fallback")) for (rows in accounts(name)) {
            val row = rows.single()
            assertEquals("kiro", row.provider)
            assertEquals("cli", row.source)
            assertEquals("ok", row.status)
            val window = row.windows.single()
            assertEquals(16.27, window.used!!, 0.0)
            assertEquals(98.373, window.remainingPercent!!, 0.000001)
            assertEquals(98, quotaPercentLeft(window))
            assertEquals("2026-12-01T00:00:00.000Z", window.resetsAt)
        }
    }

    @Test fun `Kiro reported zero is exhausted rather than missing`() {
        for (rows in accounts("kiro-zero")) {
            assertEquals("ok", rows.single().status)
            assertEquals(0, quotaPercentLeft(rows.single().windows.single()))
        }
    }

    @Test fun `Kiro failure and sign out do not invent zero quota`() {
        for ((name, status) in listOf("kiro-unreadable" to "unavailable", "kiro-signed-out" to "notConfigured")) {
            for (rows in accounts(name)) {
                assertEquals(status, rows.single().status)
                assertTrue(rows.single().windows.isEmpty())
            }
        }
    }

    @Test fun `Copilot unlimited Chat has no synthetic reset line`() {
        for (rows in accounts("copilot-no-reset")) {
            val window = rows.single().windows.single()
            assertEquals("Chat", window.label)
            assertEquals(100, quotaPercentLeft(window))
            assertEquals("", window.resetsAt)
            assertEquals("", window.resetDescription)
            assertEquals("", formatBoundary(window.resetsAt, window.boundaryKind, 0L))
        }
    }

    @Test fun `Copilot finite Premium retains its own reset beside unlimited Chat`() {
        for (rows in accounts("copilot-mixed")) {
            val windows = rows.single().windows
            val premium = windows.single { it.label == "Premium" }
            assertEquals(75, quotaPercentLeft(premium))
            assertEquals("2026-12-01T00:00:00.000Z", premium.resetsAt)
            assertTrue(windows.single { it.label == "Chat" }.resetsAt.isEmpty())
        }
    }

    @Test fun `versioned endpoints retain the existing read protocol`() {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = javaClass.getResource("/protocol/v0.84.1/$prefix$name.json")!!.readText()
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"),
                response("devices"), response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
        }
    }
}
