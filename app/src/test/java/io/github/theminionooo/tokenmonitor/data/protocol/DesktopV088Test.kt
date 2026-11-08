package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatWindowAmount
import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitDisplayProvider
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV088Test {
    @Test fun `saved released fixtures retain separate products currencies and client usage`() {
        checkRelease { name -> javaClass.getResource("/protocol/v0.88.0/$name.json")!!.readText() }
    }

    @Test fun `actual released Node and Worker reads reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Supply responses from the pinned Hub contract harness", !directory.isNullOrBlank())
        checkRelease { name -> File(directory!!, "$name.json").readText() }
    }

    private fun checkRelease(read: (String) -> String) {
        val labels = Json.parseToJsonElement(read("new-client-labels")).jsonObject
        for (id in listOf("mistral", "prime-agent", "jcode", "copilot")) {
            assertEquals(labels.getValue(id).jsonPrimitive.content, id.displayName())
        }
        assertEquals("Mistral", "mistral".providerLabel())
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = read(prefix + name)
            fun providers(name: String) = HubProtocolParser.decodeStats(response(name)).limits.providers
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"),
                response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
            val mistral = providers("mistral-allowances").single()
            assertEquals("web", mistral.source)
            val api = mistral.windows.single { it.label == "Included API" }
            val vibe = mistral.windows.single { it.label == "Included Vibe Code" }
            assertEquals(75.0, api.remainingPercent!!, 0.0)
            assertEquals("EUR", api.currency)
            assertEquals(100.0, vibe.remainingPercent!!, 0.0)
            assertTrue(api.resetsAt.isNotEmpty())
            for ((name, expected) in listOf("mistral-allowances" to 13.0, "mistral-zero" to 0.0, "mistral-negative" to -2.0)) {
                val balance = providers(name).single().windows.single { it.metric == "credits" }
                assertEquals(expected, balance.remaining!!, 0.0)
                assertEquals("EUR", balance.currency)
                assertEquals(false, balance.showMeter)
                assertNull(balance.remainingPercent)
            }
            val rows = providers("minimax-products")
            assertEquals(3, rows.size)
            assertEquals(3, rows.map(::limitAccountIdentity).distinct().size)
            val code = rows.single { it.source == "oauth" }
            assertEquals("MiniMax Code", limitDisplayProvider(code).providerLabel())
            assertEquals("MiniMax API", limitDisplayProvider(rows.first { it.source == "api" }).providerLabel())
            assertEquals("", code.plan)
            val points = code.windows.single { it.metric == "credits" }
            assertEquals("CREDITS", points.currency)
            assertEquals(4624.0, points.remaining!!, 0.0)
            assertEquals("4,624.00", formatWindowAmount(points.remaining, points.currency))
            assertEquals(17.0, code.windows.single { it.label == "Video" }.remaining!!, 0.0)
            val unlimited = code.windows.single { it.label == "Weekly" }
            assertEquals(false, unlimited.showMeter)
            assertNull(unlimited.remainingPercent)
            assertEquals(setOf(0.0, -2.0), rows.filter { it.source == "api" }.map { it.windows.single().remaining }.toSet())
            assertTrue(rows.filter { it.source == "api" }.all { it.windows.single().currency == "USD" })
            for (apiAccount in rows.filter { it.source == "api" }) {
                assertEquals(1.25, apiAccount.spend!!.today!!, 0.0)
                assertFalse(apiAccount.spend.locallyTracked)
            }
            assertNull(code.spend)
            assertTrue(providers("minimax-stale-account").any { it.status == "unavailable" && it.windows.isEmpty() })
            val today = HubProtocolParser.decodeStats(response("new-clients")).periods.getValue("today")
            assertEquals(125L, today.totalTokens)
            assertEquals(35L, today.clients.getValue("jcode"))
            assertEquals(25L, today.sessions.single { it.client == "jcode" }.outputTokens)
        }
    }
}
