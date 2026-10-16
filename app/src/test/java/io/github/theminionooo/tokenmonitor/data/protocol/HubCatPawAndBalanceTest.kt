package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop v0.70.0: the CatPaw client and the MiniMax pay-as-you-go balance. */
class HubCatPawAndBalanceTest {
    @Test fun `v0 70 fixture reads CatPaw usage on every surface`() {
        val snapshot = HubProtocolParser.decodeSnapshot(
            resource("health.json"), resource("stats.json"), resource("devices.json"),
            resource("history.json"), resource("subscriptions.json"), 0,
        )
        assertEquals("v0.70.0", snapshot.health.hubBuild)
        assertEquals("v0.72.0", HubProtocolParser.SUPPORTED_UPSTREAM_VERSION)
        val today = snapshot.today
        assertEquals(20_300L, today.totalTokens)
        assertEquals(today.totalTokens, today.sessions.sumOf { it.totalTokens })
        assertEquals(13, today.sessions.size)
        assertEquals(snapshot.today.sessions, snapshot.stats.periods.getValue("month").sessions)
        assertEquals(snapshot.today.sessions, snapshot.stats.devices.single().periods.getValue("today").sessions)

        // CatPaw models are never priced, so its tokens count as unpriced and its cost stays zero.
        assertEquals(1_200L, today.clients["catpaw"])
        assertEquals(0.0, today.clientCosts.getValue("catpaw"), 0.0)
        assertEquals(1_200L, today.models["auto"])
        assertEquals(mapOf("auto" to 1_200L), today.clientModels["catpaw"])
        assertEquals(1_200L, today.clientUnpricedTokens["catpaw"])
        assertEquals(1_200L, today.modelUnpricedTokens["auto"])
        assertEquals(1_200L, today.clientModelUnpricedTokens["catpaw"]?.get("auto"))
        assertEquals(1_600L, today.unpricedTokens)
        assertEquals(0.94, today.costUsd, 0.0001)

        val session = today.sessions.single { it.client == "catpaw" }
        assertEquals("sample-catpaw", session.id)
        assertEquals(1_200L, session.totalTokens)
        assertEquals(1_200L, session.unpricedTokens)
        assertEquals(0.0, session.costUsd, 0.0)
        assertEquals(mapOf("auto" to 1_200L), session.modelTokens)
        assertEquals("", session.projectLabel)
        assertEquals("", session.title)
        assertEquals("", session.parentSessionId)
        assertFalse(session.dotsObservedOnly)
        assertEquals(true, session.turnEnded)
        assertEquals(today.projects.sumOf { it.sessionCount } + 1, today.sessions.size)

        assertTrue("catpaw" in snapshot.stats.devices.single().trackedClients)
        val point = snapshot.history.daily.single()
        assertEquals(20_300L, point.tokens)
        assertEquals(1_200L, point.perClient.getValue("catpaw").tokens)
        assertEquals(1_200L, point.perClient.getValue("catpaw").unpricedTokens)
        assertEquals(1_200L, point.perModel.getValue("auto").tokens)
    }

    @Test fun `v0 70 fixture reads the MiniMax pay as you go balance`() {
        val stats = HubProtocolParser.decodeStats(resource("stats.json"))
        val account = stats.limits.providers.single()
        assertEquals("minimax", account.provider)
        assertEquals("ok", account.status)
        assertEquals("sha256:" + "c".repeat(64), account.accountKey)
        assertNull(account.spend)
        assertEquals("", account.productLabel)
        val balance = account.windows.single()
        assertBalance(balance, 12.5, "CNY")
        assertEquals(true, balance.showMeter)
        assertEquals("", balance.detail)
        assertEquals("", balance.boundaryKind)
        assertEquals(stats.limits, HubProtocolParser.decodeSnapshot(
            resource("health.json"), resource("stats.json"), resource("devices.json"),
            resource("history.json"), resource("subscriptions.json"), 0,
        ).stats.limits)
    }

    @Test fun `v0 70 stream and freshness retain CatPaw usage and the balance`() {
        val source = resource("stats.json")
        val expected = HubProtocolParser.decodeStats(source)
        fun event(name: String) = resource(name).lineSequence()
            .first { it.startsWith("data:") }.removePrefix("data:").trim()
        val streamed = checkNotNull(HubProtocolParser.decodeStatsStreamEvent(event("stats-stream.sse")))
        assertEquals(expected, streamed)
        assertEquals(1_200L, streamed.periods.getValue("today").clients["catpaw"])
        assertBalance(streamed.limits.providers.single().windows.single(), 12.5, "CNY")
        val refreshed = HubProtocolParser.decodeStats(HubStreamProtocol.mergeFreshness(source, event("stats-freshness.sse")))
        assertEquals("2026-10-13T12:02:00.000Z", refreshed.updatedAt)
        assertEquals(expected.periods, refreshed.periods)
        assertEquals(expected.limits, refreshed.limits)
    }

    @Test fun `v0 70 fixture keeps the v0 69 session relationships`() {
        val sessions = HubProtocolParser.decodeStats(resource("stats.json")).periods.getValue("today").sessions.associateBy { it.id }
        assertEquals("sample-codex-parent", sessions.getValue("sample-codex-subagent").parentSessionId)
        assertEquals("background-review", sessions.getValue("sample-codex-review").sessionKind)
        assertEquals(sessions.getValue("sample-codex-parent").nativeSessionId, sessions.getValue("sample-codex-continuation").nativeSessionId)
        assertEquals("claude", sessions.getValue("sample-t3-codex").parentClient)
        assertEquals("waiting", sessions.getValue("sample-claude-live").liveActivity?.state)
        assertEquals(true, sessions.getValue("sample-codex-waiting").waitingForInput)
        assertEquals(mapOf("grok-bot-1" to 500L, "claude-sonnet-4-5" to 200L), sessions.getValue("sample-cursor-grok-bot").modelTokens)
        assertTrue(sessions.getValue("dots-example").dotsObservedOnly)
    }

    @Test fun `earlier fixtures have no CatPaw usage and no MiniMax balance`() {
        val versions = listOf(
            "v0.54.0", "v0.55.0", "v0.56.0", "v0.60.0", "v0.61.0", "v0.62.0", "v0.63.0", "v0.63.1",
            "v0.64.0", "v0.65.0", "v0.66.0", "v0.67.0", "v0.68.0", "v0.69.0",
        )
        for (version in versions) {
            val stats = HubProtocolParser.decodeStats(resource("stats.json", version))
            for ((name, period) in stats.periods) {
                assertFalse("$version $name", "catpaw" in period.clients)
                assertTrue("$version $name", period.sessions.none { it.client == "catpaw" })
            }
            for (window in stats.limits.providers.filter { it.provider == "minimax" }.flatMap { it.windows }) {
                assertFalse("$version ${window.label}", window.metric == "credits")
            }
        }
        assertTrue(HubProtocolParser.decodeStats(resource("stats.json", "v0.69.0")).limits.providers.isEmpty())
    }

    @Test fun `MiniMax balances keep their currency and sign`() {
        assertBalance(minimaxWindow("""{"remaining":12.5,"currency":"CNY"}"""), 12.5, "CNY")
        assertBalance(minimaxWindow("""{"remaining":3.75,"currency":"USD"}"""), 3.75, "USD")
        // Zero and overdrawn balances are real readings, not missing ones.
        assertBalance(minimaxWindow("""{"remaining":0,"currency":"USD"}"""), 0.0, "USD")
        assertBalance(minimaxWindow("""{"remaining":-3.2,"currency":"USD"}"""), -3.2, "USD")
        assertBalance(minimaxWindow("""{"remaining":1234567.89,"currency":"CNY"}"""), 1234567.89, "CNY")
        // No amount means no reading; the window itself is still reported.
        assertNull(minimaxWindow("""{"currency":"CNY"}""").remaining)
        assertNull(minimaxWindow("""{"remaining":null,"currency":"CNY"}""").remaining)
        assertEquals("", minimaxWindow("""{"remaining":4}""").currency)
    }

    @Test fun `MiniMax pay as you go ignores unknown fields and still reads a Token Plan account`() {
        val payg = minimaxAccount(
            """{"accountLabel":"Pay-as-you-go","region":"cn","futureField":{"nested":[1,2]},"windows":[
                {"kind":"billing","metric":"credits","label":"Balance","remaining":12.5,"currency":"CNY","showMeter":true,"futureWindowField":1}]}""",
        )
        assertEquals(1, payg.windows.size)
        assertBalance(payg.windows.single(), 12.5, "CNY")
        // The billing type is the plan the desktop prints beside the provider.
        assertEquals("Pay-as-you-go", payg.plan)

        val plan = minimaxAccount(
            """{"accountLabel":"Token Plan","windows":[
                {"kind":"session","label":"5h","usedPercent":20,"remainingPercent":80,"resetsAt":"2026-10-13T15:00:00.000Z","windowMinutes":300,"showMeter":true},
                {"kind":"weekly","label":"Weekly","usedPercent":10,"remainingPercent":90,"resetsAt":"2026-10-20T12:00:00.000Z","windowMinutes":10080,"showMeter":true}]}""",
        )
        assertEquals("Token Plan", plan.plan)
        assertEquals(listOf("session", "weekly"), plan.windows.map { it.kind })
        assertEquals(listOf(80.0, 90.0), plan.windows.map { it.remainingPercent })
        assertTrue(plan.windows.none { it.metric == "credits" })
        assertTrue(plan.windows.all { it.remaining == null })
    }

    @Test fun `an unrecognised client with unpriced usage still decodes`() {
        val period = HubProtocolParser.decodeStats(
            """{"periods":{"today":{"totalTokens":50,"clients":{"future-client":50},"models":{"auto":50},
                "clientModels":{"future-client":{"auto":50}},"sessions":{"future-client:s":{"sessionId":"s","client":"future-client","totalTokens":50,"models":{"auto":50}}}}}}""",
        ).periods.getValue("today")
        assertEquals(50L, period.clients["future-client"])
        assertEquals("future-client", period.sessions.single().client)
    }

    private fun assertBalance(window: LimitWindow, amount: Double, currency: String) {
        assertEquals("billing", window.kind)
        assertEquals("credits", window.metric)
        assertEquals("Balance", window.label)
        assertEquals(amount, window.remaining!!, 0.0)
        assertEquals(currency, window.currency)
        assertNull(window.usedPercent)
        assertNull(window.remainingPercent)
        assertEquals("", window.resetsAt)
    }

    private fun minimaxWindow(fields: String): LimitWindow = minimaxAccount(
        """{"windows":[{"kind":"billing","metric":"credits","label":"Balance",${fields.trim().removePrefix("{").removeSuffix("}")}}]}""",
    ).windows.single()

    private fun minimaxAccount(fields: String): LimitAccount = HubProtocolParser.decodeStats(
        """{"limits":{"providers":[{"provider":"minimax","status":"ok","source":"api",${fields.trim().removePrefix("{").removeSuffix("}")}}]}}""",
    ).limits.providers.single()

    private fun resource(name: String, version: String = "v0.70.0"): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("protocol/$version/$name"),
    ).bufferedReader().use { it.readText() }
}
