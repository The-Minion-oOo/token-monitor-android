package io.github.theminionooo.tokenmonitor.data.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal const val MAX_STREAM_JSON_DEPTH = 64
internal const val MAX_STREAM_EVENT_CHARS = 2 * 1024 * 1024 + 16 * 1024

/** Check received text before the JSON parser can allocate a deeply nested tree. */
internal fun requireStreamJsonLimits(raw: String) {
    if (raw.length > MAX_STREAM_EVENT_CHARS) streamJsonLimit()
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in raw) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> if (++depth > MAX_STREAM_JSON_DEPTH) streamJsonLimit()
            '}', ']' -> if (--depth < 0) streamJsonLimit()
        }
    }
    if (depth != 0 || quoted) streamJsonLimit()
}

/** Patches may otherwise build deeper trees across many individually shallow frames. */
internal fun requireStreamTreeDepth(value: JsonElement, parentDepth: Int = 0) {
    fun children(element: JsonElement): Iterator<JsonElement>? = when (element) {
        is JsonObject -> element.values.iterator()
        is JsonArray -> element.iterator()
        else -> null
    }
    val root = children(value) ?: return
    val stack = ArrayDeque<Iterator<JsonElement>>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        if (parentDepth + stack.size > MAX_STREAM_JSON_DEPTH) streamJsonLimit()
        val current = stack.last()
        if (!current.hasNext()) stack.removeLast()
        else children(current.next())?.let(stack::addLast)
    }
}

internal fun encodeStreamStats(stats: JsonObject): String {
    requireStreamTreeDepth(stats)
    return stats.toString()
}

private fun streamJsonLimit(): Nothing = throw HubProtocolException("The Hub stream exceeds the phone's JSON limits.")
