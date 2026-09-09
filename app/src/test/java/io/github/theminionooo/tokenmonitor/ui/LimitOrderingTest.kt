package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitOrderingTest {
    @Test
    fun accountsWithQuotaWindowsAppearFirstWithoutChangingGroupOrder() {
        val accounts = listOf(
            account("Antigravity"),
            account("Claude", hasWindow = true),
            account("Codex", hasWindow = true),
            account("Ollama"),
        )

        assertEquals(
            listOf("Claude", "Codex", "Antigravity", "Ollama"),
            prioritizeAvailableLimits(accounts).map { it.provider },
        )
    }

    private fun account(provider: String, hasWindow: Boolean = false) = LimitAccount(
        provider = provider,
        accountName = "",
        accountEmail = "",
        plan = "",
        status = "",
        sourceDeviceId = "",
        updatedAt = "",
        windows = if (hasWindow) listOf(window()) else emptyList(),
    )

    private fun window() = LimitWindow(
        kind = "weekly",
        label = "Weekly",
        usedPercent = 25.0,
        remainingPercent = 75.0,
        remaining = null,
        resetsAt = "",
        metric = "percent",
        currency = "",
        detail = "",
        showMeter = true,
    )
}
