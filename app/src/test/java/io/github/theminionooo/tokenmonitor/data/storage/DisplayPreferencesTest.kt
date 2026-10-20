package io.github.theminionooo.tokenmonitor.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun homeListCountsKeepDefaultsAndClampEachListSeparately() {
        val defaults = DisplayOptions()
        assertEquals(5, defaults.homeListCount("Tools"))
        assertEquals(3, defaults.homeListCount("Devices"))
        val custom = defaults.copy(homeListCounts = mapOf("Tools" to 0, "Models" to 99, "Sessions" to 17))
        assertEquals(1, custom.homeListCount("Tools"))
        assertEquals(50, custom.homeListCount("Models"))
        assertEquals(17, custom.homeListCount("Sessions"))
        assertEquals(3, custom.homeListCount("Devices"))
    }
    @Test
    fun togglingAnItemPreservesTheCurrentOrder() {
        val allowed = listOf("Home", "Tools", "Status", "Devices")
        val custom = listOf("Home", "Status", "Tools", "Devices")

        val hidden = toggleOrderedValue(custom, "Tools", enabled = false, allowed)
        val restored = toggleOrderedValue(hidden, "Tools", enabled = true, allowed)

        assertEquals(listOf("Home", "Status", "Devices"), hidden)
        assertEquals(listOf("Home", "Status", "Devices", "Tools"), restored)
    }

    @Test
    fun unknownStoredItemsAreDiscarded() {
        val result = toggleOrderedValue(
            current = listOf("Home", "Removed view", "Tools"),
            value = "Home",
            enabled = true,
            allowed = listOf("Home", "Tools"),
        )

        assertEquals(listOf("Home", "Tools"), result)
    }
}
