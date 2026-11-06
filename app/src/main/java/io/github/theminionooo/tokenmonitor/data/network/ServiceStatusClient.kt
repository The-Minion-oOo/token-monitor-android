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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.jsonObject

internal data class ServiceStatusProvider(
    val id: String,
    val label: String,
    val pageUrl: String,
    val summaryUrl: String,
    val format: ServiceStatusFormat = ServiceStatusFormat.Statuspage,
    val componentIds: Set<String> = emptySet(),
)

internal enum class ServiceStatusFormat { Statuspage, Atom, Instatus, OpenCode }

/** Reads public status feeds while the Status screen is visible. */
internal class ServiceStatusClient {
    private val requestSlots = Semaphore(4)

    suspend fun load(): ServiceStatusSnapshot = coroutineScope {
        val checkedAt = System.currentTimeMillis()
        val results = serviceStatusProviders.map { provider ->
            async {
                try {
                    requestSlots.withPermit {
                        runInterruptible(Dispatchers.IO) {
                            summarizeServiceProvider(provider, fetch(provider), checkedAt)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    unknownProvider(provider, checkedAt)
                }
            }
        }.awaitAll()
        ServiceStatusSnapshot(results, checkedAt)
    }

    private fun fetch(provider: ServiceStatusProvider): String {
        val connection = URL(provider.summaryUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = true
            connection.instanceFollowRedirects = provider.format != ServiceStatusFormat.Atom
            connection.setRequestProperty("Accept", if (provider.format == ServiceStatusFormat.Atom) "application/atom+xml" else "application/json")
            connection.setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) error("Status endpoint returned HTTP ${connection.responseCode}")
            readBounded(connection.inputStream)
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
    }
}

internal val deepseekStatusProvider = ServiceStatusProvider(
    "deepseek", "DeepSeek", "https://status.deepseek.com", "https://status.deepseek.com/feed.atom", ServiceStatusFormat.Atom,
)

// Public provider endpoints from the desktop v0.87.0 Status catalog.
internal val serviceStatusProviders = listOf(
    statuspage("claude", "Claude", "https://status.claude.com"),
    statuspage("openai", "OpenAI", "https://status.openai.com"),
    ServiceStatusProvider("opencode", "OpenCode", "https://status.opencode.ai", "https://status.opencode.ai/api/status", ServiceStatusFormat.OpenCode),
    statuspage("cursor", "Cursor", "https://status.cursor.com"),
    statuspage("factory", "Factory Droid", "https://status.factory.ai"),
    statuspage("kimi", "Kimi", "https://status.moonshot.cn"),
    statuspage("github", "Copilot", "https://www.githubstatus.com", setOf("pjmpxvq2cmr2", "cnnb39dkkk82")),
    ServiceStatusProvider("zed", "Zed", "https://status.zed.dev", "https://status.zed.dev/v3/summary.json", ServiceStatusFormat.Instatus),
    deepseekStatusProvider,
    statuspage("devin", "Devin", "https://www.devinstatus.com"),
    statuspage("minimax", "MiniMax (China)", "https://status.minimaxi.com"),
    statuspage("minimax-intl", "MiniMax (International)", "https://status.minimax.io"),
)

private fun statuspage(id: String, label: String, pageUrl: String, componentIds: Set<String> = emptySet()) =
    ServiceStatusProvider(id, label, pageUrl, "$pageUrl/api/v2/summary.json", componentIds = componentIds)

internal fun summarizeServiceProvider(provider: ServiceStatusProvider, body: String, checkedAt: Long): ServiceProviderStatus =
    try {
        if (provider.format == ServiceStatusFormat.Atom) summarizeAtomFeedProvider(provider, body, checkedAt)
        else summarizeJsonStatus(provider, Json.parseToJsonElement(body).jsonObject, checkedAt)
    } catch (_: Exception) {
        unknownProvider(provider, checkedAt)
    }

internal fun unknownProvider(provider: ServiceStatusProvider, checkedAt: Long) = ServiceProviderStatus(
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
