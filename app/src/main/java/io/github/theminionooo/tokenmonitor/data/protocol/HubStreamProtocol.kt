package io.github.theminionooo.tokenmonitor.data.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Applies incremental stream frames without teaching the stable snapshot parser about delivery modes. */
internal object HubStreamProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun normalizeComplete(raw: String): String = statsObject(raw).toString()

    /**
     * Stream v2 freshness frames contain only transport timestamps and stale state.
     * Merge those bounded fields into the last complete wire snapshot so a small
     * freshness event can never erase periods, limits, sessions, or history.
     */
    fun mergeFreshness(currentRaw: String, freshnessRaw: String): String {
        val current = statsObject(currentRaw)
        val freshness = statsObject(freshnessRaw)
        val merged = current.toMutableMap()
        listOf("updatedAt", "staleAfterMs").forEach { key -> freshness[key]?.let { merged[key] = it } }

        freshness.objectField("limits")?.let { incoming ->
            merged["limits"] = JsonObject(current.objectField("limits").orEmpty() + incoming)
        }
        freshness["devices"]?.let { element ->
            val incoming = (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val byId = incoming.associateBy(::deviceId)
            val existing = current.array("devices").mapNotNull { it as? JsonObject }
            val combined = existing.map { device ->
                byId[deviceId(device)]?.let { JsonObject(device + it) } ?: device
            }.toMutableList()
            val known = existing.map(::deviceId).toSet()
            combined += incoming.filter { deviceId(it) !in known }
            merged["devices"] = JsonArray(combined)
        }
        return JsonObject(merged).toString()
    }

    private fun statsObject(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw HubProtocolException("The Hub returned a JSON value where an object was expected.")
        return root.objectField("stats") ?: root
    }

    private fun JsonObject.objectField(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    private fun deviceId(device: JsonObject): String = device["deviceId"]?.jsonPrimitive?.contentOrNull
        ?.ifBlank { null } ?: device["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
}
