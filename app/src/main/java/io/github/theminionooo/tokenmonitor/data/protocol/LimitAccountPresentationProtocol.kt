package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.domain.LimitAccountPresentation
import io.github.theminionooo.tokenmonitor.domain.LimitProviderPresentation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun decodeLimitAccountPresentation(raw: String): LimitAccountPresentation? {
    val (revision, document) = decodeSettingsDocument(raw) ?: return null
    if (document == JsonNull) return LimitAccountPresentation(revision, emptyMap())
    val value = document as? JsonObject ?: return null
    val providers = value.mapNotNull { (provider, element) ->
        val group = element as? JsonObject ?: return@mapNotNull null
        val labels = (group["labels"] as? JsonObject)?.entries.orEmpty().take(200).mapNotNull label@{ (key, entry) ->
            val name = entry.presentationString()?.trim() ?: return@label null
            val id = accountKey(key) ?: return@label null
            if (name.codePointCount(0, name.length) > 32 || name.any(Char::isISOControl) ||
                '@' in name || "://" in name) return@label null
            id to name
        }.toMap()
        val order = (group["order"] as? JsonArray).orEmpty().take(200)
            .mapNotNull { it.presentationString()?.let(::accountKey) }.distinct()
            .withIndex().associate { (index, key) -> key to index }
        provider to LimitProviderPresentation(labels, order)
    }.toMap()
    return LimitAccountPresentation(revision, providers)
}

private fun accountKey(value: String): String? = value.trim().takeIf { it.length in 1..256 }

private fun JsonElement.presentationString(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
