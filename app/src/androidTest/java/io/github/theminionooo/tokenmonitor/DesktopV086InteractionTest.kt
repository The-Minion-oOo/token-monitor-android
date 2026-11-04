package io.github.theminionooo.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.network.deepseekStatusProvider
import io.github.theminionooo.tokenmonitor.data.network.summarizeServiceProvider
import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopV086InteractionTest {
    @Test fun officialFeedUsesAndroidXmlParserAndMatchesReleasedSummaries() {
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open("protocol/v0.86.0/deepseek-status.json")
            .bufferedReader().use { it.readText() }
        for (element in Json.parseToJsonElement(text).jsonArray) {
            val row = element.jsonObject
            val value = summarizeServiceProvider(deepseekStatusProvider, row["body"]!!.jsonPrimitive.content, 123)
            assertEquals(row["name"]!!.jsonPrimitive.content, row["health"]!!.jsonPrimitive.content, value.health.name.lowercase())
            assertEquals(row["description"]!!.jsonPrimitive.content, value.description)
            assertEquals(row["incidentCount"]!!.jsonPrimitive.int, value.incidentCount)
            assertEquals(row["maintenanceCount"]!!.jsonPrimitive.int, value.maintenanceCount)
            assertEquals(row["affectedComponents"]!!.jsonPrimitive.int, value.affectedComponents)
        }
    }
    @Test fun externalEntitiesAndDeepXmlNeverBecomeHealthyOnAndroid() {
        for (body in listOf("""<!DOCTYPE feed [<!ENTITY x SYSTEM "https://example.invalid/secret">]><feed>&x;</feed>""",
            """<feed xmlns="http://www.w3.org/2005/Atom">""" + "<a>".repeat(20) + "</a>".repeat(20) + "</feed>",
            """<feed xmlns="http://www.w3.org/2005/Atom"><entry xmlns="urn:unknown"><summary>&lt;strong&gt;Status:&lt;/strong&gt; resolved</summary></entry></feed>""")) {
            assertEquals(ServiceHealth.Unknown, summarizeServiceProvider(deepseekStatusProvider, body, 0).health)
        }
    }
}
