package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.*
import org.junit.Test

class ProviderDetailsTest {
    @Test fun `reset credits retain zero expiry and grant eligibility separately from quota windows`() {
        val account = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"claude","resetCredits":{"availableCount":0,"nextExpiresAt":"2026-12-01T00:00:00Z","expirations":["2026-12-01T00:00:00Z"],"grants":[{"id":"g","label":"Weekly grant","resetsLeft":0,"resetsTotal":2,"usableNow":false,"useRequiresLimit":true,"clears":["weekly"]}]}}]}}""").limits.providers.single()
        assertEquals(0L, account.resetCredits!!.availableCount)
        assertEquals(false, account.resetCredits.grants.single().usableNow)
        assertEquals(true, account.resetCredits.grants.single().useRequiresLimit)
        assertTrue(account.windows.isEmpty())
        assertEquals(listOf("weekly"), account.resetCredits.grants.single().clears)
    }

    @Test fun `reported token summaries retain real zeros and ignore invalid amounts`() {
        val usage = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"typesafe","usageSummary":{"period":"month","totalTokens":1200,"todayTokens":0,"weekTokens":300,"inputTokens":800,"outputTokens":400,"requests":4,"standardCost":"NaN"}}]}}""").limits.providers.single().usageSummary!!
        assertEquals(1200L, usage.totalTokens)
        assertEquals(0L, usage.todayTokens)
        assertEquals(4L, usage.requests)
        assertNull(usage.standardCost)
        val older = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"codex"}]}}""").limits.providers.single()
        assertNull(older.resetCredits)
        assertNull(older.usageSummary)
    }
}
