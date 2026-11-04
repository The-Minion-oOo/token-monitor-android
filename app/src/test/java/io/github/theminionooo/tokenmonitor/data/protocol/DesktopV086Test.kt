package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.network.deepseekStatusProvider
import io.github.theminionooo.tokenmonitor.data.network.summarizeServiceProvider
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopV086Test {
    @Test fun `released billing cycles retain reported balance reset and meter without assuming a month length`() {
        for (prefix in listOf("", "worker/")) for (name in listOf("billing-28", "billing-29", "billing-30", "billing-31", "billing-missing", "billing-invalid", "billing-reversed")) {
            val text = javaClass.getResource("/protocol/v0.86.0/$prefix$name.json")!!.readText()
            val window = HubProtocolParser.decodeStats(text).limits.providers.single().windows.single { it.label == "Monthly" }
            assertEquals(8.5, window.remaining!!, 0.0)
            assertEquals("USD", window.currency)
            assertTrue(window.resetsAt.isNotEmpty())
            assertEquals(true, window.showMeter)
            // Android does not chart quota history; the released wire retains its exact optional duration.
            val wire = Json.parseToJsonElement(text).jsonObject["limits"]!!.jsonObject["providers"]!!.jsonArray.single()
                .jsonObject["windows"]!!.jsonArray.single { it.jsonObject["label"]!!.jsonPrimitive.content == "Monthly" }.jsonObject
            val days = name.removePrefix("billing-").toIntOrNull()
            if (days != null) assertEquals(days * 1440, wire["windowMinutes"]!!.jsonPrimitive.int)
            else assertEquals(JsonNull, wire["windowMinutes"])
        }
    }
    @Test fun `official DeepSeek feed summaries match the released desktop`() {
        val cases = Json.parseToJsonElement(javaClass.getResource("/protocol/v0.86.0/deepseek-status.json")!!.readText()).jsonArray
        for (element in cases) {
            val row = element.jsonObject
            val value = summarizeServiceProvider(deepseekStatusProvider, row["body"]!!.jsonPrimitive.content, 123)
            assertEquals(row["name"]!!.jsonPrimitive.content, row["health"]!!.jsonPrimitive.content.lowercase(), value.health.name.lowercase())
            assertEquals(row["description"]!!.jsonPrimitive.content, value.description)
            assertEquals(row["incidentCount"]!!.jsonPrimitive.int, value.incidentCount)
            assertEquals(row["maintenanceCount"]!!.jsonPrimitive.int, value.maintenanceCount)
            assertEquals(row["affectedComponents"]!!.jsonPrimitive.int, value.affectedComponents)
        }
    }
    @Test fun `released endpoints keep the read protocol`() {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = javaClass.getResource("/protocol/v0.86.0/$prefix$name.json")!!.readText()
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
        }
    }
}
