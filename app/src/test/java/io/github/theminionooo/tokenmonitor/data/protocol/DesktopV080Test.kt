package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.limitDisplayProvider

import io.github.theminionooo.tokenmonitor.ui.limitAccountIdentity
import io.github.theminionooo.tokenmonitor.ui.limitReportedAmount
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import org.junit.Assert.*
import org.junit.Test

class DesktopV080Test {
    private fun response(name: String) = javaClass.getResource("/protocol/v0.80.0/$name")!!.readText()
    private fun accounts(name: String) = listOf("", "worker/").map { prefix ->
        HubProtocolParser.decodeStats(response("$prefix$name.json")).limits.providers
    }

    @Test fun `Node and Worker endpoints and freshness remain compatible`() {
        for (prefix in listOf("", "worker/")) {
            val snapshot = HubProtocolParser.decodeSnapshot(response("${prefix}health.json"), response("${prefix}stats.json"),
                response("${prefix}devices.json"), response("${prefix}history.json"), response("${prefix}subscriptions.json"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
            fun frame(file: String) = response(file).lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
            val full = if (prefix.isEmpty()) frame("stats-stream.sse") else response("worker/stream-snapshot.json")
            val fresh = if (prefix.isEmpty()) frame("stats-freshness.sse") else response("worker/stream-freshness.json")
            val merged = HubStreamProtocol.mergeFreshness(HubStreamProtocol.normalizeComplete(full), fresh)
            assertEquals(snapshot.stats.periods, HubProtocolParser.decodeStats(merged).periods)
        }
    }

    @Test fun `Muse observations keep separate device identities and quota semantics`() {
        for (rows in accounts("muse-devices")) {
            assertEquals(2, rows.size)
            assertEquals(2, rows.map { it.sourceDeviceId }.distinct().size)
            assertEquals(2, rows.map(::limitAccountIdentity).distinct().size)
            assertTrue(rows.all { it.provider == "muse" && it.accountKey.isBlank() })
            assertEquals("Muse Code", limitDisplayProvider(rows.first()).providerLabel())
            assertEquals("Muse Code High Usage", rows.first().plan)
            assertEquals(listOf(75.0, 20.0), rows.first().windows.map { it.remainingPercent })
            assertEquals("", rows.first().windows.first().resetsAt)
        }
    }

    @Test fun `unknown Muse allowance is not a full or exhausted quota`() {
        for (rows in accounts("muse-unknown")) {
            assertEquals("unavailable", rows.single().status)
            assertTrue(rows.single().windows.isEmpty())
        }
    }

    @Test fun `Ollama API credits retain money spend and plan separately`() {
        for (rows in accounts("ollama-credits")) {
            val account = rows.single()
            assertEquals("pro", account.plan)
            val included = account.windows.single { it.label == "Included credits" }
            assertEquals(15.0, included.remaining!!, 0.0)
            assertEquals(15.0, limitReportedAmount(account.provider, included)!!, 0.0)
            assertEquals(75.0, included.remainingPercent!!, 0.0)
            assertEquals("USD", included.currency)
            val wallet = account.windows.single { it.label == "Credit balance" }
            assertEquals(3.25, wallet.remaining!!, 0.0)
            assertEquals(false, wallet.showMeter)
            assertNull(wallet.remainingPercent)
            val spent = account.windows.single { it.metric == "spend" }
            assertEquals(5.0, spent.used!!, 0.0)
            assertEquals(5.0, limitReportedAmount(account.provider, spent)!!, 0.0)
            assertNull(spent.remaining)
            assertEquals(false, spent.showMeter)
        }
    }

    @Test fun `zero and negative purchased credits are retained without an invented meter`() {
        for ((name, amount) in listOf("ollama-zero" to 0.0, "ollama-negative" to -1.0)) for (rows in accounts(name)) {
            val window = rows.single().windows.single()
            assertEquals(amount, window.remaining!!, 0.0)
            assertEquals(false, window.showMeter)
            assertNull(window.remainingPercent)
        }
    }

    @Test fun `Ollama legacy quota zero remains exhausted and missing weekly stays absent`() {
        for (rows in accounts("ollama-legacy")) {
            val window = rows.single().windows.single()
            assertEquals("session", window.kind)
            assertEquals(0.0, window.remainingPercent!!, 0.0)
            assertEquals("", window.resetsAt)
        }
    }

    @Test fun `Cookie credits and Claude managed accounts retain their identities`() {
        val api = accounts("ollama-credits")
        val cookies = accounts("ollama-cookie")
        for (index in api.indices) {
            assertEquals(api[index].single().accountKey, cookies[index].single().accountKey)
            assertEquals("pro", cookies[index].single().plan)
            assertEquals(5.0, cookies[index].single().windows.single { it.metric == "spend" }.used!!, 0.0)
        }
        for (rows in accounts("claude-accounts")) {
            assertEquals(listOf("a@example.com", "b@example.com", "c@example.com"), rows.map { it.accountEmail }.sorted())
            assertEquals(3, rows.map(::limitAccountIdentity).distinct().size)
        }
        for (rows in accounts("claude-deduped")) assertEquals(2, rows.size)
    }

    @Test fun `spend is optional and cannot be negative or fabricated from remaining`() {
        fun window(fields: String) = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"ollama","windows":[{$fields}]}]}}""")
            .limits.providers.single().windows.single()
        assertNull(window("\"metric\":\"spend\",\"remaining\":4").used)
        assertNull(window("\"used\":-1").used)
        assertEquals(0.0, window("\"used\":0").used!!, 0.0)
    }
}
