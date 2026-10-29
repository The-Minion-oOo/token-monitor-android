package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.data.network.HubStreamEvent
import kotlinx.serialization.json.*

/** One instance per SSE connection. REST and presentation never replace this baseline. */
internal class StatsStreamReceiver {
    private val json = Json { ignoreUnknownKeys = true }
    private var baseline: JsonObject? = null
    private var revision: Long? = null
    private var baselineChars = 0

    fun receive(event: HubStreamEvent): HubStreamEvent? {
        if (event.type !in setOf("snapshot", "stats", "message", "delta", "freshness")) return null
        requireStreamJsonLimits(event.data)
        val envelope = json.parseToJsonElement(event.data) as? JsonObject ?: invalid()
        val next: JsonObject
        val nextRevision: Long?
        var nextChars: Int
        when (event.type) {
            "snapshot", "stats", "message" -> {
                next = (envelope["stats"] ?: envelope) as? JsonObject ?: invalid()
                nextRevision = envelope["revision"]?.let { revisionNumber(it) ?: invalid() }
                // Received envelope/whitespace makes this a conservative size budget, without serializing again.
                nextChars = event.data.length
            }
            "delta" -> {
                nextRevision = requireNext(envelope)
                val patch = envelope["patch"] as? JsonArray ?: invalid()
                val updated = patchStats(baseline ?: invalid(), patch, baselineChars)
                next = updated.first
                nextChars = updated.second
            }
            else -> {
                val current = baseline ?: invalid()
                val versioned = "revision" in envelope
                nextRevision = if (versioned || revision != null) requireNext(envelope) else null
                val clocks = envelope["stats"] as? JsonObject ?: invalid()
                next = HubStreamProtocol.mergeFreshness(current, clocks)
                nextChars = baselineChars + jsonSizeChange(current, next)
            }
        }
        if (nextChars > MAX_BASELINE_CHARS) invalid()
        baseline = next
        baselineChars = nextChars
        revision = nextRevision
        // Immutable trees can wait in the coalescer. Serialize/decode only the latest one.
        return HubStreamEvent(if (event.type == "snapshot") "snapshot" else "stats", "", next, sourceType = event.type)
    }

    private fun requireNext(envelope: JsonObject): Long {
        val current = revision ?: invalid()
        val base = revisionNumber(envelope["baseRevision"]) ?: invalid()
        val next = revisionNumber(envelope["revision"]) ?: invalid()
        if (base != current || current >= MAX_REVISION || next != current + 1) invalid()
        return next
    }

    private fun revisionNumber(value: JsonElement?): Long? = (value as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull?.takeIf { it in 0..MAX_REVISION }

    private companion object { const val MAX_REVISION = 9_007_199_254_740_991L }
}

/** RFC 6902 add/remove/replace, applied atomically to immutable JSON containers. */
internal fun applyStatsPatch(stats: JsonObject, patch: JsonArray): JsonObject {
    return patchStats(stats, patch, encodeStreamStats(stats).length).first
}

private const val MAX_BASELINE_CHARS = MAX_STREAM_EVENT_CHARS

/** Freshness reuses untouched nodes; count only changed leaves without serializing the baseline. */
private fun jsonSizeChange(before: JsonElement, after: JsonElement): Int {
    if (before === after) return 0
    if (before is JsonObject && after is JsonObject) {
        var change = (after.size - 1).coerceAtLeast(0) - (before.size - 1).coerceAtLeast(0)
        for ((key, value) in before) {
            change += if (key in after) jsonSizeChange(value, after.getValue(key))
                else -(JsonPrimitive(key).toString().length + 1 + value.toString().length)
        }
        for ((key, value) in after) if (key !in before) change += JsonPrimitive(key).toString().length + 1 + value.toString().length
        return change
    }
    if (before is JsonArray && after is JsonArray && before.size == after.size) {
        return before.indices.sumOf { jsonSizeChange(before[it], after[it]) }
    }
    return after.toString().length - before.toString().length
}

private fun patchStats(stats: JsonObject, patch: JsonArray, initialChars: Int): Pair<JsonObject, Int> {
    if (patch.size > 4096) invalid()
    var result: JsonElement = stats
    var chars = initialChars
    for (element in patch) {
        val entry = element as? JsonObject ?: invalid()
        val op = (entry["op"] as? JsonPrimitive)?.contentOrNull
        val path = (entry["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
        if (op !in setOf("add", "remove", "replace") || op != "remove" && "value" !in entry) invalid()
        if (path.isEmpty()) {
            if (op == "remove") invalid()
            requireStreamTreeDepth(entry.getValue("value"))
            result = entry.getValue("value")
            chars = result.toString().length
            if (chars > MAX_BASELINE_CHARS) invalid()
            continue
        }
        if (!path.startsWith('/') || Regex("~(?![01])").containsMatchIn(path)) invalid()
        val keys = path.substring(1).split('/').map { it.replace("~1", "/").replace("~0", "~") }
        if (keys.size > MAX_STREAM_JSON_DEPTH) invalid()
        if (op != "remove") requireStreamTreeDepth(entry.getValue("value"), keys.size)
        fun edit(parent: JsonElement, depth: Int): JsonElement {
            val key = keys[depth]
            val leaf = depth == keys.lastIndex
            return when (parent) {
                is JsonObject -> {
                    if ((!leaf || op != "add") && key !in parent) invalid()
                    val copy = parent.toMutableMap()
                    if (!leaf) copy[key] = edit(parent.getValue(key), depth + 1)
                    else {
                        val keyChars = JsonPrimitive(key).toString().length + 1
                        when {
                            op == "remove" -> { chars -= keyChars + parent.getValue(key).toString().length + if (parent.size > 1) 1 else 0; copy.remove(key) }
                            key in parent -> { chars += entry.getValue("value").toString().length - parent.getValue(key).toString().length; copy[key] = entry.getValue("value") }
                            else -> { chars += keyChars + entry.getValue("value").toString().length + if (parent.isNotEmpty()) 1 else 0; copy[key] = entry.getValue("value") }
                        }
                    }
                    JsonObject(copy)
                }
                is JsonArray -> {
                    val append = leaf && op == "add" && key == "-"
                    val index = if (append) parent.size else key.takeIf { it.matches(Regex("0|[1-9][0-9]*")) }?.toIntOrNull() ?: invalid()
                    if (index !in 0..parent.size || index == parent.size && !(leaf && op == "add")) invalid()
                    val copy = parent.toMutableList()
                    if (!leaf) copy[index] = edit(parent[index], depth + 1)
                    else when (op) {
                        "add" -> { chars += entry.getValue("value").toString().length + if (parent.isNotEmpty()) 1 else 0; copy.add(index, entry.getValue("value")) }
                        "remove" -> { chars -= parent[index].toString().length + if (parent.size > 1) 1 else 0; copy.removeAt(index) }
                        else -> { chars += entry.getValue("value").toString().length - parent[index].toString().length; copy[index] = entry.getValue("value") }
                    }
                    JsonArray(copy)
                }
                else -> invalid()
            }
        }
        result = edit(result, 0)
        if (chars > MAX_BASELINE_CHARS) invalid()
    }
    return (result as? JsonObject ?: invalid()) to chars
}

private fun invalid(): Nothing = throw HubProtocolException("The Hub stream needs a new snapshot.")
