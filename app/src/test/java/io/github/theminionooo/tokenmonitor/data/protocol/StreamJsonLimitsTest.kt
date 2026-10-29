package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.network.HubStreamEvent
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StreamJsonLimitsTest {
    private fun nested(depth: Int) = "[".repeat(depth) + "0" + "]".repeat(depth)

    @Test fun `deep objects and arrays are rejected before parsing or recursive serialization`() {
        for (value in listOf(nested(10_000), "{\"a\":".repeat(10_000) + "0" + "}".repeat(10_000))) {
            for (type in listOf("snapshot", "stats", "message", "delta", "freshness")) {
                assertThrows(HubProtocolException::class.java) { StatsStreamReceiver().receive(HubStreamEvent(type, value)) }
            }
        }
        var tree: JsonElement = JsonPrimitive(0)
        repeat(10_000) { tree = JsonArray(listOf(tree)) }
        val root = JsonObject(mapOf("a" to tree))
        assertThrows(HubProtocolException::class.java) { encodeStreamStats(root) }
    }

    @Test fun `quotes escaped slashes and braces inside strings do not count as nesting`() {
        val value = "braces [ { \" \\ ".repeat(1000)
        val raw = "{\"stats\":{\"label\":${JsonPrimitive(value)}}}"
        val result = StatsStreamReceiver().receive(HubStreamEvent("snapshot", raw))!!
        assertEquals(value, result.completeStats!!.getValue("label").jsonPrimitive.content)
        requireStreamJsonLimits("[".repeat(MAX_STREAM_JSON_DEPTH) + "0" + "]".repeat(MAX_STREAM_JSON_DEPTH))
        assertThrows(HubProtocolException::class.java) { requireStreamJsonLimits(nested(MAX_STREAM_JSON_DEPTH + 1)) }
    }

    @Test fun `successive shallow patches cannot build an overdeep baseline or advance a rejected revision`() {
        val receiver = StatsStreamReceiver()
        receiver.receive(HubStreamEvent("snapshot", """{"revision":0,"stats":{}}"""))
        for (index in 0 until MAX_STREAM_JSON_DEPTH - 1) {
            val path = "/a".repeat(index + 1)
            receiver.receive(HubStreamEvent("delta", """{"baseRevision":$index,"revision":${index + 1},"patch":[{"op":"add","path":"$path","value":{}}]}"""))
        }
        val revision = MAX_STREAM_JSON_DEPTH - 1
        assertThrows(HubProtocolException::class.java) {
            receiver.receive(HubStreamEvent("delta", """{"baseRevision":$revision,"revision":${revision + 1},"patch":[{"op":"add","path":"${"/a".repeat(MAX_STREAM_JSON_DEPTH)}","value":{}}]}"""))
        }
        val valid = receiver.receive(HubStreamEvent("delta", """{"baseRevision":$revision,"revision":${revision + 1},"patch":[{"op":"add","path":"/ok","value":true}]}"""))!!
        assertTrue(valid.completeStats!!.getValue("ok").jsonPrimitive.boolean)
    }

    @Test fun `received whitespace is counted toward the complete event budget`() {
        val raw = """{"stats":{}}"""
        val padded = " ".repeat(MAX_STREAM_EVENT_CHARS - raw.length) + raw
        val receiver = StatsStreamReceiver()
        assertNotNull(receiver.receive(HubStreamEvent("snapshot", padded)))
        // Old stringify-based counting discarded the received padding and admitted this growth.
        assertThrows(HubProtocolException::class.java) {
            receiver.receive(HubStreamEvent("freshness", """{"stats":{"updatedAt":"new"}}"""))
        }
        assertThrows(HubProtocolException::class.java) { StatsStreamReceiver().receive(HubStreamEvent("snapshot", "$padded ")) }
    }
}
