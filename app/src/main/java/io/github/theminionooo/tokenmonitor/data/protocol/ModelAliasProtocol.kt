package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.ModelAliasGrouping
import io.github.theminionooo.tokenmonitor.domain.ModelAliasSettings
import io.github.theminionooo.tokenmonitor.domain.modelMatchKey
import io.github.theminionooo.tokenmonitor.domain.modelText
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun decodeModelAliases(raw: String): ModelAliasSettings? {
    val (revision, document) = decodeSettingsDocument(raw) ?: return null
    if (document == JsonNull) return ModelAliasSettings(revision, emptyMap(), ModelAliasGrouping.Off)
    val value = document as? JsonObject ?: return null
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
