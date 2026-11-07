package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.ModelAliasGrouping
import io.github.theminionooo.tokenmonitor.domain.ModelAliasSettings
import io.github.theminionooo.tokenmonitor.domain.modelMatchKey
import io.github.theminionooo.tokenmonitor.domain.modelText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal fun decodeModelAliases(raw: String): ModelAliasSettings? {
    val root = runCatching {
        requireStreamJsonLimits(raw)
        Json.parseToJsonElement(raw) as? JsonObject
    }.getOrNull() ?: return null
    if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true ||
        (root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != 1L) return null
    val revision = (root["revision"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        ?.takeIf { it in 0..9_007_199_254_740_991L } ?: return null
    if (root["value"] == JsonNull) return ModelAliasSettings(revision, emptyMap(), ModelAliasGrouping.Off)
    val value = root["value"] as? JsonObject ?: return null
    val mode = when ((value["modelAliasGrouping"] as? JsonPrimitive)?.content) {
        "off" -> ModelAliasGrouping.Off
        "duplicates" -> ModelAliasGrouping.Duplicates
        "prefix" -> ModelAliasGrouping.Prefix
        else -> return null
    }
    val aliases = value["modelAliases"] as? JsonObject ?: return null
    val manual = LinkedHashMap<String, String>()
    for ((source, target) in aliases) {
        val label = (target as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::modelText) ?: continue
        val id = modelText(source)
        val key = modelMatchKey(id)
        if (id.length !in 1..256 || label.length !in 1..256 || key == modelMatchKey(label)) continue
        manual.putIfAbsent(key, label)
        if (manual.size == 4096) break
    }
    return ModelAliasSettings(revision, manual, mode)
}
