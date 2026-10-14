package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageFormattingTest {
    @Test fun compactTokensKeepEachUnitBelowAThousand() {
        assertEquals("9,999", formatCompactTokens(9_999))
        assertEquals("10.0K", formatCompactTokens(10_000))
        assertEquals("12.3K", formatCompactTokens(12_345))
        assertEquals("999.9K", formatCompactTokens(999_949))
        assertEquals("1.0M", formatCompactTokens(1_000_000))
        assertEquals("214.9M", formatCompactTokens(214_949_999))
        assertEquals("999.9M", formatCompactTokens(999_949_999))
        assertEquals("1.0B", formatCompactTokens(1_000_000_000))
    }

    @Test fun compactTokensThatRoundUpToAThousandMoveToTheNextUnit() {
        assertEquals("1.0M", formatCompactTokens(999_950))
        assertEquals("1.0M", formatCompactTokens(999_960))
        assertEquals("1.0M", formatCompactTokens(999_999))
        assertEquals("1.0B", formatCompactTokens(999_950_000))
        assertEquals("1.0B", formatCompactTokens(999_999_999))
    }

    @Test fun balancesPrintLikeTheDesktopInTheirOwnCurrency() {
        assertEquals("¥12.50", formatWindowAmount(12.5, "CNY"))
        assertEquals("¥1,234,567.89", formatWindowAmount(1_234_567.89, "cny"))
        assertEquals("$0.00", formatWindowAmount(0.0, "USD"))
        assertEquals("680.00", formatWindowAmount(680.0, "CREDITS"))
        assertEquals("EUR 3.20", formatWindowAmount(3.2, "EUR"))
        assertEquals("¥-3.20", formatWindowAmount(-3.2, "CNY"))
        // Without a currency the amount is a plain count.
        assertEquals("1,500", formatWindowAmount(1_500.0, ""))
        assertEquals("CatPaw", "catpaw".displayName())
    }

    @Test fun billionsHaveNoLargerUnitToCarryInto() {
        assertEquals("12.3B", formatCompactTokens(12_345_678_901))
        assertEquals("1000.0B", formatCompactTokens(999_999_999_999))
    }

    @Test fun uniqueKeysLeaveDistinctKeysAloneAndSuffixRepeats() {
        assertEquals(listOf("a", "b", "c"), uniqueKeys(listOf("a", "b", "c")) { it })
        assertEquals(listOf("a", "a#2", "b", "a#3"), uniqueKeys(listOf("a", "a", "b", "a")) { it })
        assertEquals(emptyList<String>(), uniqueKeys(emptyList<String>()) { it })
    }

    @Test fun aSuffixedKeyNeverCollidesWithARealOne() {
        val keys = uniqueKeys(listOf("a", "a#2", "a")) { it }
        assertEquals(3, keys.toSet().size)
        assertEquals(listOf("a", "a#2", "a#3"), keys)
    }

    @Test fun signedOutProvidersFromTwoDevicesGetDistinctRowKeys() {
        val signedOut = LimitAccount(
            provider = "claude", accountName = "", accountEmail = "", plan = "", status = "notConfigured",
            sourceDeviceId = "", updatedAt = "", windows = emptyList(),
        )
        val accounts = listOf(signedOut.copy(sourceDeviceId = "desktop"), signedOut.copy(sourceDeviceId = "laptop"))
        assertEquals(1, accounts.map(::limitAccountIdentity).toSet().size)
        val keys = uniqueKeys(accounts, ::limitAccountIdentity)
        assertEquals(2, keys.toSet().size)
        assertEquals(limitAccountIdentity(accounts[0]), keys[0])
        assertTrue(keys[1].startsWith(keys[0]))
    }
}
