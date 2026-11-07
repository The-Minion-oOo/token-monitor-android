package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.sessionModelMetadata
import io.github.theminionooo.tokenmonitor.ui.matchesSearch
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ModelAliasTest {
    private fun fixture(name: String): String {
        val generated = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")
        return if (generated != null) java.io.File(generated, "$name.json").readText()
        else checkNotNull(javaClass.getResource("/protocol/v0.87.0/$name.json")).readText()
    }

    @Test fun `Node and Worker GET envelopes match their stats revisions`() {
        for (prefix in listOf("", "worker/")) {
            val empty = checkNotNull(decodeModelAliases(fixture(prefix + "model-aliases-empty")))
            assertEquals(0L, empty.revision)
            assertTrue(empty.aliases.isEmpty())
            val settings = checkNotNull(decodeModelAliases(fixture(prefix + "model-aliases")))
            val stats = HubProtocolParser.decodeStats(fixture(prefix + "model-aliases-stats"))
            assertEquals(stats.modelAliasRevision, settings.revision)
            assertEquals("GPT Five", settings.resolver(listOf("gpt-5")).name("provider/gpt-5"))
        }
    }

    @Test fun `folding follows every released desktop resolver case`() {
        for (element in Json.parseToJsonElement(fixture("model-alias-resolver")).jsonArray) {
            val row = element.jsonObject
            val payload = buildJsonObject {
                put("ok", true)
                put("version", 1)
                put("revision", 1)
                putJsonObject("value") {
                    put("modelAliases", row.getValue("aliases"))
                    put("modelAliasGrouping", row.getValue("grouping"))
                }
            }
            val ids = row.getValue("ids").jsonArray.map { it.jsonPrimitive.content }
            val resolver = checkNotNull(decodeModelAliases(payload.toString())).resolver(ids)
            assertEquals(row.getValue("expected").jsonArray.map { it.jsonPrimitive.content }, ids.map(resolver::name))
        }
    }

    @Test fun `display folding adds reported amounts without changing totals source ids or sessions`() {
        val names = checkNotNull(decodeModelAliases(fixture("model-aliases"))).resolver(listOf("gpt-5", "provider/gpt-5"))
        val period = UsagePeriod(totalTokens = 110, costUsd = 5.0, unpricedTokens = 10,
            models = mapOf("gpt-5" to 40L, "provider/gpt-5" to 70L),
            modelCosts = mapOf("gpt-5" to 2.0, "provider/gpt-5" to 3.0),
            modelUnpricedTokens = mapOf("provider/gpt-5" to 10L),
            modelThroughput = mapOf("gpt-5" to ThroughputCounters(20, 10, 1000), "provider/gpt-5" to ThroughputCounters(40, 20, 2000)))
        val display = names.usage(period)
        assertEquals(mapOf("GPT Five" to 110L), display.models)
        assertEquals(mapOf("GPT Five" to 5.0), display.modelCosts)
        assertEquals(mapOf("GPT Five" to 10L), display.modelUnpricedTokens)
        assertEquals(ThroughputCounters(60, 30, 3000), display.modelThroughput["GPT Five"])
        assertEquals(period.totalTokens, display.totalTokens)
        assertEquals(period.costUsd, display.costUsd, 0.0)
        assertEquals(2, period.models.size)
        val session = SessionUsage("source-id", client = "codex", projectLabel = "Example", totalTokens = 40, costUsd = 2.0,
            modelNames = listOf("gpt-5"), messageCount = 1, startedAt = "", lastUsedAt = "", reasoningEffort = "high")
        assertEquals("GPT Five · high", sessionModelMetadata(session, names))
        assertEquals(listOf("gpt-5"), session.modelNames)
        assertEquals("source-id", session.id)
        assertSame(period, ModelNames().usage(period))
    }

    @Test fun `conversation labels fold models once while preserving grouping and source search`() {
        val names = ModelAliasSettings(1, mapOf("gpt-5" to "GPT Five", "gpt-five" to "One hop only"), ModelAliasGrouping.Off)
            .resolver(emptyList())
        val parent = SessionUsage("main", client = "codex", projectLabel = "Example", totalTokens = 40, costUsd = 1.0,
            modelNames = listOf("gpt-5"), modelTokens = mapOf("gpt-5" to 40), messageCount = 1,
            startedAt = "2026-11-06T12:00:00Z", lastUsedAt = "2026-11-06T12:00:00Z")
        val child = parent.copy(id = "child", parentSessionId = "main")
        val row = io.github.theminionooo.tokenmonitor.ui.groupSessions(listOf(parent, child)).single()
        val labels = io.github.theminionooo.tokenmonitor.ui.sessionRowPresentation(row, names = names)
        assertTrue(labels.title.contains("GPT Five"))
        assertFalse(labels.title.contains("One hop only"))
        assertEquals(80L, row.totalTokens)
        assertEquals(setOf("main", "child"), row.sessions.map { it.id }.toSet())
        assertEquals(mapOf("gpt-5" to 80L), row.modelTokens)
        assertTrue(parent.matchesSearch("gpt-5", true, names))
        assertTrue(parent.matchesSearch("GPT Five", true, names))
    }

    @Test fun `old Hubs unknown versions and malformed settings leave raw names`() {
        assertNull(HubProtocolParser.decodeStats("""{"periods":{}}""").modelAliasRevision)
        for (body in listOf("{}", "null", "[]", "not-json",
            """{"ok":true,"version":2,"revision":1,"value":null}""",
            """{"ok":true,"version":1,"revision":"1","value":null}""",
            """{"ok":true,"version":1,"revision":-1,"value":null}""",
            """{"ok":true,"version":1,"revision":1,"value":{"modelAliases":{},"modelAliasGrouping":"future"}}""")) {
            assertNull(body, decodeModelAliases(body))
        }
        val settings = checkNotNull(decodeModelAliases(fixture("model-aliases").trimEnd().dropLast(1) + ",\"future\":true}"))
        assertEquals("unchanged-model", settings.resolver(emptyList()).name("unchanged-model"))
    }

    @Test fun `automatic alias limit counts source entries even when normalized keys collide`() {
        val ids = (0 until 4096).map { bits -> "provider" + (0 until 12).joinToString("") {
            if (bits and (1 shl it) == 0) "_" else "-"
        } + "/gpt-5" } + "vendor/solo"
        val names = ModelAliasSettings(1, emptyMap(), ModelAliasGrouping.Prefix).resolver(ids)
        assertEquals("gpt-5", names.name(ids.first()))
        assertEquals("vendor/solo", names.name(ids.last()))
    }

    @Test fun `normalization rejects invalid pairs and keeps the first spelling`() {
        val settings = checkNotNull(decodeModelAliases("""{"ok":true,"version":1,"revision":1,"value":{"modelAliasGrouping":"off","modelAliases":{" GPT.5 ":" First ","gpt-5":"Second","same":"SAME","bad":3,"empty":" "}}}"""))
        val names = settings.resolver(emptyList())
        assertEquals("First", names.name("GPT_5"))
        assertEquals("same", names.name("same"))
        assertEquals("bad", names.name("bad"))
    }
}
