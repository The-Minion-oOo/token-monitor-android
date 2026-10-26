package io.github.theminionooo.tokenmonitor.data.storage

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.*
import org.junit.Test

class DisplayListsTest {
    @Test fun `pins custom order and hidden tools never change totals`() {
        val tools = linkedMapOf("a" to 100L, "b" to 50L, "c" to 30L, "new" to 10L)
        val options = DisplayOptions(hiddenTools = setOf("a"), pinnedTools = listOf("c"), toolOrder = listOf("b", "c"))
        assertEquals(listOf("c", "b", "new"), orderedTools(tools, emptyMap(), options).map { it.key })
        assertEquals(190L, tools.values.sum())
        assertEquals(listOf("b", "c"), moveDisplayItem(listOf("c", "b"), "b", -1))
    }
    @Test fun `hiding one quota does not mutate the Hub account or hide unrelated providers`() {
        val accounts = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"codex","windows":[{"kind":"weekly","label":"Week"},{"kind":"session","label":"Session"}]},{"provider":"claude","windows":[{"kind":"weekly","label":"Week"}]}]}}""").limits.providers
        val options = DisplayOptions(hiddenLimitItems = setOf(limitItemKey("codex", accounts[0].windows[0])))
        assertEquals(listOf(1, 1), visibleLimitAccounts(accounts, options).map { it.windows.size })
        assertEquals(2, accounts[0].windows.size)
        assertEquals(1, visibleLimitAccounts(accounts, options.copy(hiddenProviders = setOf("claude"))).size)
    }
    @Test fun `persisted labels round trip punctuation and malformed settings use safe defaults`() {
        val values = listOf("a,b", "[quota]", "今日", "a,b")
        assertEquals(values.distinct(), decodeDisplayList(encodeDisplayList(values)))
        assertTrue(decodeDisplayList("broken").isEmpty())
    }
}
