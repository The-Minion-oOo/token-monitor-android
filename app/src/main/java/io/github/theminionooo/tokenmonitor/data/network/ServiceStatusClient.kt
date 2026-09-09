package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import io.github.theminionooo.tokenmonitor.domain.ServiceProviderStatus
import io.github.theminionooo.tokenmonitor.domain.ServiceStatusSnapshot
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal data class ServiceStatusProvider(
    val id: String,
    val label: String,
    val pageUrl: String,
    val summaryUrl: String,
)

/** Reads the same public Statuspage summaries as the desktop Status view. */
internal class ServiceStatusClient {
    suspend fun load(): ServiceStatusSnapshot = coroutineScope {
        val checkedAt = System.currentTimeMillis()
        val results = providers.map { provider ->
            async {
                try {
                    val payload = runInterruptible(Dispatchers.IO) { fetch(provider.summaryUrl) }
                    summarizeStatuspageProvider(provider, payload, checkedAt)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    unknownProvider(provider, checkedAt)
                }
            }
        }.awaitAll()
        ServiceStatusSnapshot(results, checkedAt)
    }

    private fun fetch(url: String): JsonObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) error("Status endpoint returned HTTP ${connection.responseCode}")
            json.parseToJsonElement(readBounded(connection.inputStream)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxResponseBytes) error("Status response is too large")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private companion object {
        const val timeoutMs = 5_000
        const val maxResponseBytes = 1 * 1024 * 1024
        val json = Json { ignoreUnknownKeys = true; isLenient = false }
        val providers = listOf(
            ServiceStatusProvider("claude", "Claude", "https://status.claude.com", "https://status.claude.com/api/v2/summary.json"),
            ServiceStatusProvider("openai", "OpenAI", "https://status.openai.com", "https://status.openai.com/api/v2/summary.json"),
            ServiceStatusProvider("cursor", "Cursor", "https://status.cursor.com", "https://status.cursor.com/api/v2/summary.json"),
            ServiceStatusProvider("deepseek", "DeepSeek", "https://status.deepseek.com", "https://deepseek.statuspage.io/api/v2/summary.json"),
        )
    }
}

internal fun summarizeStatuspageProvider(
    provider: ServiceStatusProvider,
    payload: JsonObject,
    checkedAt: Long,
): ServiceProviderStatus {
    val status = payload.objectAt("status")
    val indicator = status.stringAt("indicator").lowercase()
    val components = payload.arrayAt("components").objects()
    val incidents = payload.arrayAt("incidents").objects().filterNot { it.stringAt("status").lowercase() in inactiveIncidentStatuses }
    val maintenance = payload.arrayAt("scheduled_maintenances").objects().filterNot { it.stringAt("status").lowercase() in inactiveMaintenanceStatuses }
    val affected = components.count { it.stringAt("status").lowercase() !in nonIssueComponentStatuses }
    val incidentTitle = incidents.firstOrNull()?.stringAt("name").orEmpty()
    val description = incidentTitle.ifBlank { status.stringAt("description").ifBlank { "Unknown" } }
    return ServiceProviderStatus(
        id = provider.id,
        label = provider.label,
        pageUrl = provider.pageUrl,
        health = when (indicator) {
            "none" -> ServiceHealth.Ok
            "minor" -> ServiceHealth.Degraded
            "major", "critical" -> ServiceHealth.Outage
            else -> ServiceHealth.Unknown
        },
        description = description,
        affectedComponents = affected,
        incidentCount = incidents.size,
        maintenanceCount = maintenance.size,
        checkedAt = checkedAt,
    )
}

private fun unknownProvider(provider: ServiceStatusProvider, checkedAt: Long) = ServiceProviderStatus(
    id = provider.id,
    label = provider.label,
    pageUrl = provider.pageUrl,
    health = ServiceHealth.Unknown,
    description = "Unable to check status",
    affectedComponents = 0,
    incidentCount = 0,
    maintenanceCount = 0,
    checkedAt = checkedAt,
)

private fun JsonObject.objectAt(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.arrayAt(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.stringAt(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty().trim()
private fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

private val nonIssueComponentStatuses = setOf("operational", "under_maintenance")
private val inactiveIncidentStatuses = setOf("resolved", "completed", "postmortem")
private val inactiveMaintenanceStatuses = setOf("completed", "canceled")
