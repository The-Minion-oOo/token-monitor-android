package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.ThroughputCounters
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Test

class DesktopV084Test {
    private fun accounts(name: String) = listOf("", "worker/").map { prefix ->
        HubProtocolParser.decodeStats(response(prefix + name)).limits.providers
    }
    private fun response(name: String) = javaClass.getResource("/protocol/v0.84.0/$name.json")!!.readText()

    @Test fun `Manus daily and monthly allowances are separate from spendable credits`() {
        for (rows in accounts("manus-credits")) {
            val row = rows.single()
            assertEquals("manus", row.provider)
            assertEquals(60.0, row.windows.single { it.kind == "daily" }.remainingPercent!!, 0.0)
            assertEquals(75.0, row.windows.single { it.label == "Monthly" }.remainingPercent!!, 0.0)
            val credits = row.windows.single { it.metric == "credits" }
            assertEquals(1180.0, credits.remaining!!, 0.0)
            assertEquals("CREDITS", credits.currency)
            assertEquals(false, credits.showMeter)
            assertNull(quotaPercentLeft(credits))
            assertEquals("", credits.resetsAt)
            assertEquals("Manus", limitDisplayProvider(row).providerLabel())
        }
    }

    @Test fun `zero negative and ceilingless Manus credits do not invent quota meters`() {
        for ((name, expected) in listOf("zero" to 0.0, "negative" to -5.0, "no-ceiling" to 250.0)) {
            for (rows in accounts("manus-$name")) {
                val window = rows.single().windows.single()
                assertEquals(expected, window.remaining!!, 0.0)
                assertNull(window.remainingPercent)
                assertNull(quotaPercentLeft(window))
                assertEquals(false, window.showMeter)
            }
        }
    }

    @Test fun `missing invalid and expired Manus responses remain unknown rather than zero`() {
        for (name in listOf("missing", "invalid", "expired")) for (rows in accounts("manus-$name")) {
            assertEquals(if (name == "expired") "unauthorized" else "unavailable", rows.single().status)
            assertTrue(rows.single().windows.isEmpty())
        }
    }

    @Test fun `Manus account identity deduplicates desktop and web observations`() {
        for (rows in accounts("manus-devices")) {
            assertEquals(1, rows.size)
            assertTrue(rows.single().accountKey.isNotBlank())
        }
    }

    @Test fun `Muse web product and CLI devices retain distinct row state and labels`() {
        for (rows in accounts("muse-products")) {
            assertEquals(3, rows.size)
            assertEquals(3, rows.map(::limitAccountIdentity).toSet().size)
            assertEquals(listOf("Muse", "Muse Code", "Muse Code"), rows.map { limitDisplayProvider(it).providerLabel() }.sorted())
            val cli = rows.filter { it.source == "cli" }
            assertEquals(2, cli.map { it.sourceDeviceId }.toSet().size)
            val web = rows.single { it.source == "web" }
            assertEquals("Maximum", web.plan)
            assertEquals(limitAccountIdentity(web), limitAccountIdentity(web.copy(sourceDeviceId = "another-device")))
            assertNotEquals(limitAccountIdentity(cli.first()), limitAccountIdentity(cli.first().copy(sourceDeviceId = "another-device")))
        }
    }

    @Test fun `Muse additional tokens stay text beside the original dollar balance`() {
        for (rows in accounts("muse-products")) {
            val web = rows.single { it.source == "web" }
            val weekly = web.windows.single { it.kind == "weekly" }
            assertEquals(97.0, weekly.remainingPercent!!, 0.0)
            assertEquals("2.8B tokens left", weekly.resetDescription)
            assertNull(weekly.remaining)
            val additional = web.windows.single { it.metric == "credits" }
            assertEquals("1.5B", museAdditionalTokens(web, additional))
            assertEquals(60.0, additional.remaining!!, 0.0)
            assertEquals("USD", additional.currency)
            assertEquals(false, additional.showMeter)
            assertNull(quotaPercentLeft(additional))
            assertNull(museAdditionalTokens(web.copy(source = "cli"), additional))
        }
    }

    @Test fun `Muse zero and monetary fallback remain distinct from missing quota`() {
        for (rows in accounts("muse-web-zero")) {
            val web = rows.single()
            assertEquals(0.0, web.windows.first().remainingPercent!!, 0.0)
            val balance = web.windows.single { it.metric == "credits" }
            assertEquals(0.0, balance.remaining!!, 0.0)
            assertEquals("0", museAdditionalTokens(web, balance))
        }
        for (rows in accounts("muse-web-money-only")) {
            val web = rows.single()
            val balance = web.windows.single { it.metric == "credits" }
            assertNull(museAdditionalTokens(web, balance))
            assertEquals("$60.00", formatWindowAmount(balance.remaining!!, balance.currency))
        }
        for (name in listOf("missing", "expired")) for (rows in accounts("muse-web-$name")) {
            assertEquals(if (name == "expired") "unauthorized" else "unavailable", rows.single().status)
            assertTrue(rows.single().windows.isEmpty())
        }
    }

    @Test fun `Muse CLI refresh does not retire web readings and absent web leaves CLI intact`() {
        for (rows in accounts("muse-stale-web")) assertEquals(setOf("cli", "web"), rows.map { it.source }.toSet())
        for (rows in accounts("muse-cli-only")) assertEquals("cli", rows.single().source)
    }

    @Test fun `older Muse records retain their CLI label and device key`() {
        val row = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"muse","sourceDeviceId":"sample","windows":[]}]}}""").limits.providers.single()
        assertEquals("", row.source)
        assertEquals("Muse Code", limitDisplayProvider(row).providerLabel())
        assertEquals(limitAccountIdentity(row), limitAccountIdentity(row.copy(source = "cli")))
    }

    @Test fun `corrected throughput counters keep positive rates and empty completions unknown`() {
        for (prefix in listOf("", "worker/")) {
            val period = HubProtocolParser.decodeStats(response(prefix + "corrected-throughput")).periods.getValue("today")
            assertEquals(120L, period.timedOutputTokens)
            assertEquals(1200L, period.timedDurationMs)
            assertEquals(100.0, sessionTokenRate(period.sessions.single { it.outputTokens > 0 })!!, 0.0)
            assertNull(sessionTokenRate(period.sessions.single { it.outputTokens == 0L }))
            val tracker = LiveThroughputTracker()
            tracker.offer(period.copy(timedTokens = 0, timedOutputTokens = 0, timedDurationMs = 0,
                modelThroughput = period.modelThroughput.mapValues { ThroughputCounters(0, 0, 0) }), 0)
            assertEquals(100.0, tracker.offer(period, 1000)!!.outputPerSecond, 0.0)
        }
    }

    @Test fun `released v084 endpoint fixtures remain readable`() {
        for (prefix in listOf("", "worker/")) {
            val snapshot = HubProtocolParser.decodeSnapshot(response(prefix+"health"), response(prefix+"stats"),
                response(prefix+"devices"), response(prefix+"history"), response(prefix+"subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
        }
    }
}
