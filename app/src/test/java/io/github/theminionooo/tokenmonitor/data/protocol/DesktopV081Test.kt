package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.StreamUpdateCoalescer
import io.github.theminionooo.tokenmonitor.data.network.HubStreamEvent
import io.github.theminionooo.tokenmonitor.ui.sessionModelMetadata
import io.github.theminionooo.tokenmonitor.ui.sessionRowLabels
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DesktopV081Test {
    private val json = Json
    private fun fixture(name: String) = javaClass.getResource("/protocol/v0.81.0/$name.json")!!.readText()
    private fun event(type: String, body: String) = HubStreamEvent(type, body)
    private fun snapshot(revision: Int = 1) = event("snapshot", """{"revision":$revision,"stats":{"total":0,"rows":[1,2],"a/b~c":{"old":1}}}""")
    private fun delta(base: Int, patch: String, revision: Int = base + 1) = event("delta", """{"baseRevision":$base,"revision":$revision,"patch":$patch}""")
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected a stream recovery") } catch (_: HubProtocolException) { }
    }

    @Test fun `released Node and Worker revision chains exactly match upstream reconstruction`() {
        for (kind in listOf("node", "worker")) verifyChain(fixture("v4-$kind"))
    }

    internal fun verifyChain(raw: String) {
        val receiver = StatsStreamReceiver()
        for (value in json.parseToJsonElement(raw).jsonArray) {
            val frame = value.jsonObject
            val actual = receiver.receive(event(frame.getValue("event").jsonPrimitive.content, frame.getValue("data").toString()))!!
            fun canonical(value: JsonElement): JsonElement = when (value) {
                is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
                is JsonArray -> JsonArray(value.map(::canonical))
                else -> value
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical(actual.completeStats!!).toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(frame.getValue("expectedSha256").jsonPrimitive.content, digest)
            HubProtocolParser.decodeStats(actual.completeStats.toString())
        }
    }

    @Test fun `patches preserve zero null escaped keys and array edits`() {
        val receiver = StatsStreamReceiver(); receiver.receive(snapshot())
        val next = receiver.receive(delta(1, """[{"op":"replace","path":"/total","value":0},{"op":"remove","path":"/a~1b~0c/old"},{"op":"add","path":"/a~1b~0c/new","value":null},{"op":"add","path":"/rows/1","value":0},{"op":"remove","path":"/rows/0"},{"op":"add","path":"/rows/-","value":3}]"""))!!.completeStats!!
        assertEquals(json.parseToJsonElement("""{"total":0,"rows":[0,2,3],"a/b~c":{"new":null}}"""), next)
    }

    @Test fun `invalid patch is atomic and never advances the revision`() {
        val receiver = StatsStreamReceiver(); receiver.receive(snapshot())
        rejected { receiver.receive(delta(1, """[{"op":"replace","path":"/total","value":99},{"op":"remove","path":"/missing"}]""")) }
        val next = receiver.receive(delta(1, """[{"op":"replace","path":"/total","value":1}]"""))!!
        assertEquals(1, next.completeStats!!.getValue("total").jsonPrimitive.int)
    }

    @Test fun `missing duplicate skipped and out of order revisions require recovery`() {
        for ((base, revision) in listOf(0 to 1, 1 to 1, 1 to 3, 2 to 3)) {
            val receiver = StatsStreamReceiver(); receiver.receive(snapshot())
            rejected { receiver.receive(delta(base, "[]", revision)) }
        }
        for (raw in listOf("{}", """{"baseRevision":1,"patch":[]}""", """{"baseRevision":1,"revision":"2","patch":[]}""")) {
            val receiver = StatsStreamReceiver(); receiver.receive(snapshot()); rejected { receiver.receive(event("delta", raw)) }
        }
    }

    @Test fun `malformed pointers operations roots and indexes are rejected`() {
        for (patch in listOf(
            """[{"op":"move","path":"/total","value":1}]""", """[{"op":"add","path":"/bad~2","value":1}]""",
            """[{"op":"replace","path":"/rows/01","value":1}]""", """[{"op":"remove","path":"/rows/-"}]""",
            """[{"op":"add","path":"/missing/child","value":1}]""", """[{"op":"replace","path":"","value":null}]""",
            """[{"op":"replace","path":"/rows/2","value":1}]""", """[{"op":"add","path":"/total"}]"""
        )) { val receiver = StatsStreamReceiver(); receiver.receive(snapshot()); rejected { receiver.receive(delta(1, patch)) } }
    }

    @Test fun `incremental growth cannot bypass the complete response size cap`() {
        val receiver = StatsStreamReceiver(); receiver.receive(snapshot())
        val value = JsonPrimitive("x".repeat(1_100_000)).toString()
        receiver.receive(delta(1, """[{"op":"add","path":"/large","value":$value}]"""))
        rejected { receiver.receive(delta(2, """[{"op":"add","path":"/second","value":$value}]""")) }
        assertNotNull(receiver.receive(delta(2, """[{"op":"remove","path":"/large"}]""")))
    }

    @Test fun `freshness shares revisions and renews only existing activity readings`() {
        val receiver = StatsStreamReceiver()
        receiver.receive(event("snapshot", """{"revision":4,"stats":{"periods":{"today":{"sessions":{"s":{"totalTokens":12,"liveActivity":{"state":"waiting","observedAt":"2026-10-29T00:00:00Z"}},"no-clock":{"totalTokens":2}}}},"devices":[{"deviceId":"d","clientHealth":{"observedAt":"old","status":"ok"}}]}}"""))
        val fresh = event("freshness", """{"baseRevision":4,"revision":5,"stats":{"sessionActivityObservedAt":{"today":{"s":"2026-10-29T00:00:20Z","no-clock":"2026-10-29T00:00:20Z","new":"2026-10-29T00:00:20Z"}},"devices":[{"deviceId":"d","clientHealthObservedAt":"2026-10-29T00:00:20Z"}]}}""")
        val stats = receiver.receive(fresh)!!.completeStats!!
        val sessions = stats.getValue("periods").jsonObject.getValue("today").jsonObject.getValue("sessions").jsonObject
        assertEquals(2, sessions.size)
        assertFalse("liveActivity" in sessions.getValue("no-clock").jsonObject)
        assertEquals("waiting", sessions.getValue("s").jsonObject.getValue("liveActivity").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals("2026-10-29T00:00:20Z", stats.getValue("devices").jsonArray[0].jsonObject.getValue("clientHealth").jsonObject.getValue("observedAt").jsonPrimitive.content)
        rejected { receiver.receive(fresh) }
    }

    @Test fun `coalescing never drops a required wire revision`() {
        val receiver = StatsStreamReceiver(); val coalescer = StreamUpdateCoalescer()
        coalescer.offer(receiver.receive(snapshot())!!, 0)
        for (i in 1..6) coalescer.offer(receiver.receive(delta(i, """[{"op":"replace","path":"/total","value":$i}]"""))!!, i * 100L)
        val latest = coalescer.drain(5000).single()
        assertEquals(6, latest.completeStats!!.getValue("total").jsonPrimitive.int)
    }

    @Test fun `new connections discard old baselines and older Hubs keep full snapshots`() {
        val first = StatsStreamReceiver(); first.receive(snapshot())
        val replacement = StatsStreamReceiver(); rejected { replacement.receive(delta(1, "[]")) }
        assertNotNull(replacement.receive(event("snapshot", """{"stats":{"total":0}}""")))
        rejected { replacement.receive(delta(1, "[]")) }
        assertEquals(2, replacement.receive(event("stats", """{"stats":{"total":2}}"""))!!.completeStats!!.getValue("total").jsonPrimitive.int)
        replacement.receive(snapshot(8))
        assertNotNull(replacement.receive(delta(8, "[]")))
    }

    @Test fun `reported reasoning effort survives expiry and titles without altering token usage`() {
        fun session(effort: String, client: String = "codex") = HubProtocolParser.decodeStats("""{"periods":{"today":{"sessions":{"s":{"client":"$client","models":{"gpt-5":12},"totalTokens":12,"archived":true,"reasoningEffort":$effort}}}}}""").periods.getValue("today").sessions.single()
        val row = session("\"xhigh\"")
        assertEquals("gpt-5 · xhigh", sessionModelMetadata(row))
        assertTrue(sessionRowLabels(row).title.contains("xhigh"))
        assertTrue(sessionRowLabels(row.copy(title = "Synthetic session")).meta.contains("gpt-5 · xhigh"))
        assertEquals(12L, row.totalTokens)
        for (value in listOf("null", "\"unknown\"", "\"<script>\"", "123")) assertNull(session(value).reasoningEffort)
        assertNull(session("\"high\"", "cursor").reasoningEffort)
    }

    @Test fun `personal Volcengine quotas retain zero missing windows and independent failure`() {
        for (prefix in listOf("", "worker/")) {
            fun rows(name: String) = HubProtocolParser.decodeStats(fixture("$prefix$name")).limits.providers
            val coding = rows("volc-personal").single { it.accountName == "Coding Plan" }
            assertEquals(listOf(100.0, 87.5, 0.0), coding.windows.map { it.remainingPercent })
            assertEquals(1, rows("volc-missing").single { it.accountName == "Coding Plan" }.windows.size)
            assertTrue(rows("volc-unavailable").any { it.status == "ok" && it.windows.isNotEmpty() })
            assertTrue(rows("volc-unavailable").any { it.status == "unavailable" })
        }
    }

    @Test fun `Codex price tier labels use the released legacy plan field`() {
        for (prefix in listOf("", "worker/")) {
            val accounts = HubProtocolParser.decodeStats(fixture("${prefix}codex-pro-plans")).limits.providers
            assertEquals(listOf("Pro 100", "Pro 200", "Pro 500"), accounts.map { it.plan })
            assertEquals(3, accounts.map { it.accountKey }.distinct().size)
        }
    }
}
