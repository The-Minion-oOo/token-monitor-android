package io.github.theminionooo.tokenmonitor.data.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot

/** Applies incremental stream frames without teaching the stable snapshot parser about delivery modes. */
internal object HubStreamProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun normalizeComplete(raw: String): String = encode(statsObject(raw))

    /**
     * Stream v2 freshness frames contain only transport timestamps and stale state.
     * Merge those bounded fields into the last complete wire snapshot so a small
     * freshness event can never erase periods, limits, sessions, or history.
     */
    fun mergeFreshness(currentRaw: String, freshnessRaw: String): String {
        return mergeFreshness(currentRaw, statsObject(freshnessRaw))
    }

    fun mergeFreshness(currentRaw: String, freshness: JsonObject): String =
        encode(mergeFreshness(statsObject(currentRaw), freshness))

    private fun encode(stats: JsonObject): String = json.encodeToString(JsonObject.serializer(), stats)

    fun decodeFreshness(raw: String): JsonObject = statsObject(raw)

    /** Accumulates bounded metadata; serializing the complete stats body can wait for a cache write. */
    fun mergeFreshness(current: JsonObject, freshness: JsonObject): JsonObject {
        val merged = current.toMutableMap()
        listOf("updatedAt", "staleAfterMs").forEach { key -> freshness[key]?.let { merged[key] = it } }
        current.objectField("periods")?.let { periods ->
            merged["periods"] = applyActivityClocks(periods, freshness.objectField("sessionActivityObservedAt"))
        }

        freshness.objectField("limits")?.let { incoming ->
            merged["limits"] = JsonObject(current.objectField("limits").orEmpty() + incoming)
        }
        freshness["devices"]?.let { element ->
            val incoming = (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val byId = incoming.associateBy(::deviceId)
            val existing = current.array("devices").mapNotNull { it as? JsonObject }
            val combined = existing.map { device ->
                byId[deviceId(device)]?.let { clocks ->
                    val fields = device.toMutableMap()
                    fields.putAll(clocks.filterKeys { it !in setOf("sessionActivityObservedAt", "clientHealthObservedAt") })
                    device.objectField("periods")?.let { fields["periods"] = applyActivityClocks(it, clocks.objectField("sessionActivityObservedAt")) }
                    device.objectField("clientHealth")?.let { health ->
                        clocks["clientHealthObservedAt"]?.let { fields["clientHealth"] = JsonObject(health + ("observedAt" to it)) }
                    }
                    JsonObject(fields)
                } ?: device
            }.toMutableList()
            val known = existing.map(::deviceId).toSet()
            combined += incoming.filter { deviceId(it) !in known }
            merged["devices"] = JsonArray(combined)
        }
        return JsonObject(merged)
    }

    private fun applyActivityClocks(periods: JsonObject, clocks: JsonObject?): JsonObject {
        if (clocks == null) return periods
        return JsonObject(periods.mapValues { (name, value) ->
            val period = value as? JsonObject ?: return@mapValues value
            val times = clocks.objectField(name) ?: return@mapValues value
            val sessions = period.objectField("sessions") ?: return@mapValues value
            JsonObject(period + ("sessions" to JsonObject(sessions.mapValues session@{ (key, row) ->
                val session = row as? JsonObject ?: return@session row
                val activity = session.objectField("liveActivity") ?: return@session row
                val at = (times[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString } ?: return@session row
                if (runCatching { java.time.Instant.parse(at.content) }.isFailure) return@session row
                JsonObject(session + ("liveActivity" to JsonObject(activity + ("observedAt" to at))))
            })))
        })
    }

    /** Unexpected content or a new device takes the complete decoder path instead. */
    fun applyFreshness(previous: HubSnapshot, patch: JsonObject, capturedAt: Long): HubSnapshot? {
        val limits = patch.objectField("limits")
        if (limits != null && limits.keys.any { it != "updatedAt" }) return null
        val devices = patch.array("devices").mapNotNull { it as? JsonObject }.associateBy(::deviceId)
        val deviceFields = setOf("deviceId", "id", "updatedAt", "receivedAt", "ageMs", "stale")
        val knownIds = previous.stats.devices.map { it.id }.toSet()
        if (devices.any { (id, fields) -> id !in knownIds || fields.keys.any { it !in deviceFields } }) return null
        val decoded = HubProtocolParser.decodeStats(patch)
        val decodedDevices = decoded.devices.associateBy { it.id }
        val stats = previous.stats.copy(
            updatedAt = if ("updatedAt" in patch) decoded.updatedAt else previous.stats.updatedAt,
            staleAfterMs = if ("staleAfterMs" in patch) decoded.staleAfterMs else previous.stats.staleAfterMs,
            limits = if (limits != null && "updatedAt" in limits) previous.stats.limits.copy(updatedAt = decoded.limits.updatedAt) else previous.stats.limits,
            devices = if (devices.isEmpty()) previous.stats.devices else previous.stats.devices.map { current ->
                val fields = devices[current.id] ?: return@map current
                val fresh = decodedDevices.getValue(current.id)
                current.copy(
                    updatedAt = if ("updatedAt" in fields) fresh.updatedAt else current.updatedAt,
                    receivedAt = if ("receivedAt" in fields) fresh.receivedAt else current.receivedAt,
                    ageMs = if ("ageMs" in fields) fresh.ageMs else current.ageMs,
                    stale = if ("stale" in fields) fresh.stale else current.stale,
                )
            },
        )
        return previous.copy(stats = stats, capturedAt = capturedAt, fromCache = false, stale = false)
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
