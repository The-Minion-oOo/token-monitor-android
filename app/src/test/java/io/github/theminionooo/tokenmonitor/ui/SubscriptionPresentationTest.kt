package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.domain.Subscription
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SubscriptionPresentationTest {
    private fun record() = Subscription("a", "codex", "Pro", 2000, "USD", "2026-01-31", "month", true)

    @Test fun `a top-up ledger is never a monthly zero charge`() {
        val record = HubProtocolParser.decodeSubscriptions("""{"subscriptions":[{"id":"payg","provider":"deepseek","kind":"topup","currency":"USD","amountMinor":0,"topUps":[{"id":"one","date":"2026-10-01","amountMinor":2500},{"id":"two","date":"2026-10-20","amountMinor":5000}]}]}""").entries.single()
        assertTrue(record.isTopUp())
        assertEquals("Top-ups", subscriptionCadence(record))
        assertEquals(7500L, topUpTotalMinor(record))
        assertEquals("two", record.topUps.first().id)
        assertNull(subscriptionBoundary(record, LocalDate.of(2026, 10, 26)))
    }

    @Test fun `renewals keep the original calendar anchor and apply an unexpired override`() {
        assertEquals(LocalDate.of(2026, 2, 28), subscriptionBoundary(record(), LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), subscriptionBoundary(record(), LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 4, 7), subscriptionBoundary(record().copy(nextRenewalOverride = "2026-04-07"), LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), subscriptionBoundary(record().copy(nextRenewalOverride = "2026-02-07"), LocalDate.of(2026, 3, 1)))
    }

    @Test fun `cancelled subscriptions stop projecting charges and multi-month plans retain their cadence`() {
        assertEquals(LocalDate.of(2026, 2, 28), subscriptionBoundary(record().copy(autoRenew = false), LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 5, 1), subscriptionBoundary(record().copy(autoRenew = false, endDate = "2026-05-01"), LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 4, 30), subscriptionBoundary(record().copy(intervalCount = 3), LocalDate.of(2026, 3, 1)))
        assertEquals("Every 3 months", subscriptionCadence(record().copy(intervalCount = 3)))
    }

    @Test fun `invalid payments stay absent and missing recurring prices stay unknown`() {
        val record = HubProtocolParser.decodeSubscriptions("""{"subscriptions":[{"provider":"x","topUps":[{"date":"bad","amountMinor":12},{"date":"2026-10-01","amountMinor":-3},{"date":"2026-10-02","amountMinor":0}]}]}""").entries.single()
        assertFalse(record.amountReported)
        assertEquals(1, record.topUps.size)
        assertEquals(0L, record.topUps.single().amountMinor)
    }
}
