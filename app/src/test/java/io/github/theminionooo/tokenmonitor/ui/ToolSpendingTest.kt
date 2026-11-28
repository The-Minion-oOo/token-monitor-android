package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ModelNames
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolSpendingTest {
    @Test fun shareUsesTheWholePeriodAndOnlyTheSelectedToolsKnownCost() {
        val period = UsagePeriod(
            costUsd = 10.0,
            clientCosts = mapOf("codex" to 4.0, "claude" to 2.0),
            clientUnpricedTokens = mapOf("codex" to 50L),
            models = mapOf("shared-model" to 600L),
            modelCosts = mapOf("shared-model" to 6.0),
            clientModels = mapOf("codex" to mapOf("shared-model" to 300L, "other-model" to 100L)),
            clientModelCosts = mapOf("codex" to mapOf("shared-model" to 3.0, "other-model" to 1.0)),
        )

        assertEquals(0.4, period.toolSpending("codex", fromHistory = false).costShare)
        val aliases = ModelNames(mapOf("shared-model" to "Display name", "other-model" to "Display name"))
        val models = aliases.usage(period.modelsForTool("codex"))
        assertEquals(mapOf("Display name" to 400L), models.models)
        assertEquals(mapOf("Display name" to 4.0), models.modelCosts)
        assertEquals(50L, models.unpricedTokens)
    }

    @Test fun distinguishesReportedZeroMissingPriceAndUnknownRollingZero() {
        val period = UsagePeriod(costUsd = 1.0, clientCosts = mapOf("codex" to 0.0))
        assertEquals(0.0, period.toolSpending("codex", fromHistory = false).costShare)
        assertNull(period.toolSpending("codex", fromHistory = true).costUsd)
        assertNull(period.toolSpending("claude", fromHistory = false).costUsd)
    }

    @Test fun keepsPositiveRollingCostAndWithholdsInvalidShares() {
        val period = UsagePeriod(costUsd = 2.0, clientCosts = mapOf("codex" to 1.0))
        assertEquals(0.5, period.toolSpending("codex", fromHistory = true).costShare)
        assertEquals(1.0, period.copy(costUsd = 0.0).toolSpending("codex", fromHistory = false).costUsd)
        assertNull(period.copy(costUsd = 0.0).toolSpending("codex", fromHistory = false).costShare)
        assertNull(period.copy(costUsd = 0.99).toolSpending("codex", fromHistory = false).costShare)
        assertNull(period.copy(clientCosts = mapOf("codex" to Double.NaN)).toolSpending("codex", fromHistory = false).costUsd)
        assertEquals(emptyMap<String, Long>(), period.modelsForTool("codex").models)
    }

    @Test fun usesOnlyToolComponentsAndDoesNotGuessTheResidual() {
        val period = UsagePeriod(
            clients = mapOf("codex" to 100L),
            clientCacheReads = mapOf("codex" to 25L, "claude" to 50L),
            clientCacheWrites = mapOf("codex" to 10L),
            clientOutputs = mapOf("codex" to 20L),
            modelOutputs = mapOf("global-model" to 95L),
        )
        assertEquals(ToolTokenMix(0L, 25L, 10L, 20L, 45L), period.toolSpending("codex", fromHistory = false).tokenMix)
        val classified = period.copy(clientUnclassifiedTokens = mapOf("codex" to 5L))
        assertEquals(ToolTokenMix(40L, 25L, 10L, 20L, 5L), classified.toolSpending("codex", fromHistory = false).tokenMix)
        assertNull(classified.toolSpending("codex", fromHistory = true).tokenMix)
    }
}
