package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.network.ServiceStatusFormat
import io.github.theminionooo.tokenmonitor.data.network.serviceStatusProviders
import io.github.theminionooo.tokenmonitor.data.network.summarizeServiceProvider
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopV087Test {
    @Test fun `release fixtures preserve status, quotas, and corrected history`() {
        checkRelease { name -> javaClass.getResource("/protocol/v0.87.0/$name.json")!!.readText() }
    }

    @Test fun `pinned desktop Node Hub and Worker responses reach Android`() {
        val directory = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        assumeTrue("Run the pinned Hub contract harness to supply responses", !directory.isNullOrBlank())
        checkRelease { name -> File(directory!!, "$name.json").readText() }
    }

    private fun checkRelease(read: (String) -> String) {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = read("$prefix$name")
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"),
                response("devices"), response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
            fun quota(name: String) = HubProtocolParser.decodeStats(response(name)).limits.providers.single()
            val full = quota("opendesign-windows")
            assertEquals("OpenDesign", full.provider.providerLabel())
            assertEquals("Pro", full.plan)
            assertEquals("web", full.source)
            val (session, weekly) = full.windows
            assertEquals("session", session.kind)
            assertEquals(100.0, session.remainingPercent!!, 0.0)
            assertTrue(session.resetsAt.isNotEmpty())
            assertEquals("weekly", weekly.kind)
            assertEquals(75.0, weekly.remainingPercent!!, 0.0)
            assertEquals("", weekly.resetsAt)
            assertTrue(full.windows.all { it.metric.isEmpty() && it.showMeter == true })
            val partial = quota("opendesign-weekly")
            assertNotEquals(full.accountKey, partial.accountKey)
            assertEquals(listOf("weekly"), partial.windows.map { it.kind })
            val invalid = quota("opendesign-invalid")
            assertEquals("unavailable", invalid.status)
            assertTrue(invalid.windows.isEmpty())
            val today = HubProtocolParser.decodeStats(response("delta-stats")).periods.getValue("today")
            assertEquals(0L, today.totalTokens)
            assertEquals(0.0, today.costUsd, 0.0)
            assertEquals(321L, HubProtocolParser.decodeHistory(response("delta-history")).daily.single().tokens)
        }

        val status = Json.parseToJsonElement(read("service-status")).jsonObject
        val providers = status.getValue("providers").jsonArray
        assertEquals(providers.size, serviceStatusProviders.size)
        for ((entry, provider) in providers.zip(serviceStatusProviders)) {
            val row = entry.jsonObject
            assertEquals(row.getValue("id").jsonPrimitive.content, provider.id)
            assertEquals(row.getValue("label").jsonPrimitive.content, provider.label)
            assertEquals(row.getValue("pageUrl").jsonPrimitive.content, provider.pageUrl)
            assertEquals(row.getValue("summaryUrl").jsonPrimitive.content, provider.summaryUrl)
            assertEquals(row["componentIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet(), provider.componentIds)
            assertEquals(when (row["summaryFormat"]?.jsonPrimitive?.contentOrNull) {
                "atom-feed" -> ServiceStatusFormat.Atom
                "instatus" -> ServiceStatusFormat.Instatus
                "opencode-json" -> ServiceStatusFormat.OpenCode
                else -> ServiceStatusFormat.Statuspage
            }, provider.format)
        }
        for (entry in status.getValue("cases").jsonArray) {
            val row = entry.jsonObject
            val provider = serviceStatusProviders.single { it.id == row.getValue("id").jsonPrimitive.content }
            val value = summarizeServiceProvider(provider, row.getValue("body").toString(), 0)
            assertEquals(row.getValue("name").jsonPrimitive.content, row.getValue("health").jsonPrimitive.content, value.health.name.lowercase())
            assertEquals(row.getValue("description").jsonPrimitive.content, value.description)
            assertEquals(row.getValue("incidentCount").jsonPrimitive.int, value.incidentCount)
            assertEquals(row.getValue("maintenanceCount").jsonPrimitive.int, value.maintenanceCount)
            assertEquals(row.getValue("affectedComponents").jsonPrimitive.int, value.affectedComponents)
        }
    }
}
