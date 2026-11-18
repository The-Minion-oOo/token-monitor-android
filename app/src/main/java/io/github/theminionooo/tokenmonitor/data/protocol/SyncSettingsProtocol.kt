package io.github.theminionooo.tokenmonitor.data.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal fun settingsRevision(value: JsonElement?): Long? =
    (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        ?.takeIf { it in 0..9_007_199_254_740_991L }

internal fun decodeSettingsDocument(raw: String): Pair<Long, JsonElement>? {
    val root = runCatching {
        requireStreamJsonLimits(raw)
        Json.parseToJsonElement(raw) as? JsonObject
    }.getOrNull() ?: return null
    if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true ||
        (root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != 1L) return null
    val revision = settingsRevision(root["revision"]) ?: return null
    return revision to (root["value"] ?: return null)
}
