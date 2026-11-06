package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import org.junit.Assert.*
import org.junit.Test

class ServiceStatusFeedTest {
    private fun entry(title: String, status: String, updated: String = "2026-11-04T10:00:00Z", components: String = "API, Web") =
        """<entry><title>$title</title><updated>$updated</updated><summary type="html">&lt;p&gt;&lt;strong&gt;Status:&lt;/strong&gt; $status&lt;/p&gt;&lt;p&gt;&lt;strong&gt;Affected components:&lt;/strong&gt; $components&lt;/p&gt;</summary></entry>"""
    private fun feed(vararg entries: String) = """<feed xmlns="http://www.w3.org/2005/Atom">${entries.joinToString("")}</feed>"""
    private fun parse(body: String) = summarizeServiceProvider(deepseekStatusProvider, body, 123)

    @Test fun `DeepSeek uses only the official Atom endpoint`() {
        assertEquals("https://status.deepseek.com/feed.atom", deepseekStatusProvider.summaryUrl)
        assertEquals(ServiceStatusFormat.Atom, deepseekStatusProvider.format)
    }
    @Test fun `active outage wins over resolved incidents and deduplicates components`() {
        val value = parse(feed(entry("API unavailable", "monitoring"), entry("Web degraded", "investigating"), entry("Old outage", "resolved")))
        assertEquals(ServiceHealth.Outage, value.health)
        assertEquals(2, value.incidentCount)
        assertEquals(2, value.affectedComponents)
        assertEquals(123L, value.checkedAt)
    }
    @Test fun `resolved completed canceled and postmortem entries are inactive`() {
        for (status in listOf("resolved", "completed", "postmortem", "canceled", "cancelled")) {
            val value = parse(feed(entry("Old outage", status)))
            assertEquals(ServiceHealth.Ok, value.health)
            assertEquals(0, value.incidentCount)
        }
    }
    @Test fun `maintenance counts separately from degradation`() {
        val value = parse(feed(entry("Scheduled maintenance", "monitoring"), entry("Slow responses", "investigating")))
        assertEquals(ServiceHealth.Degraded, value.health)
        assertEquals(1, value.incidentCount)
        assertEquals(1, value.maintenanceCount)
        assertEquals(0, parse(feed(entry("维护", "monitoring"))).incidentCount)
    }
    @Test fun `unknown and missing statuses never report healthy`() {
        for (status in listOf("???", "future_status", "")) assertEquals(ServiceHealth.Degraded, parse(feed(entry("Incident", status))).health)
    }
    @Test fun `latest incident supplies headline with XML entities and CDATA decoded`() {
        val value = parse(feed(entry("Old", "investigating", "2026-11-03T10:00:00Z"), entry("<![CDATA[API & Web]]>", "monitoring")))
        assertEquals("API & Web", value.description)
        assertEquals("API & Web", parse(feed(entry("API &amp; Web", "monitoring"))).description)
    }
    @Test fun `Chinese outage wording is recognized`() {
        assertEquals(ServiceHealth.Outage, parse(feed(entry("API 部分中断", "monitoring"))).health)
    }
    @Test fun `empty truncated HTML wrong-root and malformed feeds report unknown`() {
        for (body in listOf("", feed(), "<html>oops</html>", "<feed><entry/>", "<feed><entry/></feed>",
            feed(entry("Done", "resolved")).dropLast(3), feed(entry("Bad & entity", "resolved")),
            feed(entry("Done", "resolved").replace("<entry>", """<entry xmlns="urn:unknown">""")))) {
            assertEquals(body, ServiceHealth.Unknown, parse(body).health)
        }
    }
    @Test fun `DOCTYPE entity declarations deep nesting and oversized input are rejected`() {
        val external = """<!DOCTYPE feed [<!ENTITY x SYSTEM "https://example.invalid/secret">]>""" + feed(entry("&x;", "resolved"))
        for (body in listOf(external, "<!ENTITY x 'x'>" + feed(entry("Done", "resolved")),
            feed("<a>".repeat(20) + "</a>".repeat(20)), " ".repeat(1_048_577))) {
            assertEquals(ServiceHealth.Unknown, parse(body).health)
        }
    }
    @Test fun `existing JSON providers retain their status handling`() {
        val provider = ServiceStatusProvider("other", "Other", "https://example.com", "https://example.com/summary")
        assertEquals(ServiceHealth.Ok, summarizeServiceProvider(provider, """{"status":{"indicator":"none"}}""", 0).health)
        assertEquals(ServiceHealth.Unknown, summarizeServiceProvider(provider, "bad", 0).health)
    }
}
