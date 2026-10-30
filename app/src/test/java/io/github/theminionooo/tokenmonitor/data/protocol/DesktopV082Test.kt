package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.ui.sessionCacheHitPercent
import io.github.theminionooo.tokenmonitor.ui.sessionMetricLabels
import io.github.theminionooo.tokenmonitor.ui.sessionPromptCacheMinutes
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DesktopV082Test {
    private fun stats(name: String) = HubProtocolParser.decodeStats(javaClass.getResource("/protocol/v0.82.0/$name.json")!!.readText())
    private fun periods(name: String) = listOf("", "worker/").map { stats("$it$name").periods.getValue("month") }

    @Test fun `Codex cached estimates are ignored without losing cache usage or Claude estimates`() {
        for (prefix in listOf("", "worker/")) {
            val rows = stats("${prefix}legacy-cache").periods.getValue("today").sessions
            val codex = rows.single { it.client == "codex" }
            val claude = rows.single { it.client == "claude" }
            val now = Instant.parse(claude.promptCache!!.observedAt).toEpochMilli() + 60_000
            assertNull(sessionPromptCacheMinutes(codex, now))
            assertEquals(4, sessionPromptCacheMinutes(claude, now))
            assertEquals(60.0, sessionCacheHitPercent(codex)!!, 0.0)
            assertFalse(sessionMetricLabels(codex, now).contains("estimate"))
            assertNotNull(codex.promptCache) // Old wire/cache data is preserved; presentation owns the suppression.
        }
    }

    @Test fun `both archive paths retain observed model components without inflating total tokens`() {
        for (kind in listOf("session", "client")) for (period in periods("archive-$kind-observed")) {
            assertEquals(100L, period.totalTokens)
            assertEquals(mapOf("alpha" to 60L, "beta" to 40L), period.models)
            assertEquals(mapOf("alpha" to 20L, "beta" to 20L), period.modelCacheReads)
            assertEquals(mapOf("alpha" to 15L, "beta" to 5L), period.modelOutputs)
            assertTrue(period.modelUnclassifiedTokens.isEmpty())
            for ((model, input) in mapOf("alpha" to 20L, "beta" to 10L)) {
                assertEquals(input, period.models.getValue(model) - period.modelCacheReads.getValue(model) -
                    period.modelCacheWrites.getValue(model) - period.modelOutputs.getValue(model))
            }
        }
    }

    @Test fun `legacy and invalid multi model evidence remains unknown rather than proportional`() {
        for (kind in listOf("session", "client")) for (variant in listOf("legacy", "invalid")) {
            for (period in periods("archive-$kind-$variant")) {
                assertEquals(100L, period.totalTokens)
                assertEquals(mapOf("alpha" to 60L, "beta" to 40L), period.modelUnclassifiedTokens)
                assertTrue(period.modelCacheReads.isEmpty())
                assertTrue(period.modelOutputs.isEmpty())
                assertEquals(40L, period.clientCacheReads["opencode"])
            }
        }
    }

    @Test fun `partial model coverage classifies only its observed portion`() {
        for (kind in listOf("session", "client")) for (period in periods("archive-$kind-partial")) {
            assertEquals(mapOf("alpha" to 20L, "beta" to 40L), period.modelUnclassifiedTokens)
            assertEquals(mapOf("alpha" to 10L), period.modelCacheReads)
            assertEquals(mapOf("alpha" to 5L), period.modelOutputs)
        }
    }

    @Test fun `missing archived input stays unclassified`() {
        for (kind in listOf("session", "client")) for (period in periods("archive-$kind-missing-input")) {
            assertEquals(30L, period.clientUnclassifiedTokens["opencode"])
            assertEquals(30L, period.modelUnclassifiedTokens["alpha"])
        }
    }

    @Test fun `lower and zero corrections replace matching archives while unrelated usage survives`() {
        for (tokens in listOf(40L, 0L)) for (period in periods("archive-correction-$tokens")) {
            assertEquals(tokens, period.totalTokens)
            assertEquals(0.0, period.costUsd, 0.0)
            assertEquals(tokens, period.clients["opencode"] ?: 0L)
        }
        for (period in periods("archive-unrelated")) assertEquals(120L, period.totalTokens)
    }
}
