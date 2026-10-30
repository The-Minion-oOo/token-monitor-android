package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.data.protocol.StatsStreamReceiver

internal data class WireHubSnapshot(
    val health: String,
    val stats: String,
    val devices: String?,
    val history: String?,
    val subscriptions: String?,
    val capturedAt: Long,
    val freshness: JsonObject? = null,
) {
    /** Materialized only for persistence or the compatibility decoder, always on an IO thread. */
    val resolvedStats: String by lazy {
        freshness?.let { HubStreamProtocol.mergeFreshness(stats, it) } ?: stats
    }
}

internal data class HubStreamEvent(
    val type: String,
    val data: String,
    val completeStats: JsonObject? = null,
    val sourceType: String = type,
)

internal open class HubApiException(
    val statusCode: Int,
    val presentation: String,
) : Exception(presentation)

/** A GET body over the phone's size cap; optional endpoints treat it like an omitted one. */
internal class HubResponseTooLargeException : HubApiException(200, "The Hub response is too large for this phone dashboard.")

/** A deliberately small GET-only client for the Token Monitor Hub. */
internal class HubApiClient(
    private val timing: HubTimingRecorder = HubTimings.recorder,
    private val beforeRequest: (HubConnection) -> Unit = {},
) : Closeable {
    private val requests = mutableSetOf<HttpURLConnection>()
    private var closed = false
    private val received = AtomicLong()
    val receivedBytes: Long get() = received.get()

    override fun close() {
        val pending = synchronized(requests) {
            closed = true
            requests.toList().also { requests.clear() }
        }
        pending.forEach { it.disconnect() }
    }

    private fun release(http: HttpURLConnection, group: RequestGroup? = null) {
        group?.remove(http)
        synchronized(requests) { requests.remove(http) }
        http.disconnect()
    }
    suspend fun loadSnapshot(connection: HubConnection): WireHubSnapshot {
        val health = get(connection, "/api/health", authenticated = false)
        val identity = runCatching { HubProtocolParser.decodeHealth(health) }.getOrNull()
        if (identity?.ok != true || identity.role != "hub") {
            throw HubApiException(200, "The address is not a Token Monitor Hub.")
        }
        // Identity must be verified first. The four independent reads then share
        // a two-request budget and are all complete before publishing a snapshot.
        val bodies = coroutineScope {
            val permits = Semaphore(2)
            val group = RequestGroup()
            val paths = listOf("/api/stats", "/api/devices", "/api/history", "/api/subscriptions")
            try {
                paths.mapIndexed { index, path ->
                    async(Dispatchers.IO) {
                        permits.withPermit {
                            if (index == 0) get(connection, path, group = group)
                            else getOptional(connection, path, group)
                        }
                    }
                }.awaitAll()
            } finally {
                // Coroutine cancellation alone cannot interrupt a blocking socket.
                // Close this batch before coroutineScope waits for its children.
                group.close()
            }
        }
        return WireHubSnapshot(
            health = health,
            stats = requireNotNull(bodies[0]),
            devices = bodies[1],
            history = bodies[2],
            subscriptions = bodies[3],
            capturedAt = System.currentTimeMillis(),
        )
    }

    /** Widgets use aggregate limits and history, not device details or subscriptions. */
    fun loadWidgetSnapshot(connection: HubConnection, previous: WireHubSnapshot?): WireHubSnapshot {
        val health = get(connection, "/api/health", authenticated = false)
        val identity = HubProtocolParser.decodeHealth(health)
        if (!identity.ok || identity.role != "hub") throw HubApiException(200, "The address is not a Token Monitor Hub.")
        return WireHubSnapshot(health, get(connection, "/api/stats"), previous?.devices,
            getOptional(connection, "/api/history"), previous?.subscriptions, System.currentTimeMillis())
    }

    fun getSubscriptions(connection: HubConnection): String = get(connection, "/api/subscriptions")

    /** Reads the changing aggregate only; detailed history remains in the initial snapshot. */
    fun getStats(connection: HubConnection): String = get(connection, "/api/stats")

    /** A quick unauthenticated identity check used while looking for a Hub on the home network. */
    fun probeHub(baseUrl: String, timeoutMs: Int): Boolean = runCatching {
        val http = openHubConnection(URL("$baseUrl/api/health")).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs * 2
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            http.responseCode in 200..299 && HubProtocolParser.decodeHealth(readBounded(http.inputStream)).let { it.ok && it.role == "hub" }
        } finally {
            http.disconnect()
        }
    }.getOrDefault(false)

    /** Blocks until the server closes the stream or [SseSession.close] is called. */
    suspend fun streamStats(
        connection: HubConnection,
        onOpen: suspend (SseSession) -> Unit,
        onEvent: suspend (HubStreamEvent) -> Unit,
    ) {
        val request = timing.nextRequest()
        val http = timing.measure(HubTimingStage.STREAM_OPEN, HubTimingEndpoint.STREAM, request) {
            val opened = open(
                connection = connection,
                path = "/api/stats/stream",
                authenticated = true,
                accept = "text/event-stream",
                headers = mapOf("x-token-monitor-stream" to "4"),
                request = request,
            )
            var accepted = false
            try {
                var status = 0
                timing.measure(HubTimingStage.HEADERS, HubTimingEndpoint.STREAM, request, status = { status }) { status = opened.responseCode }
                if (status !in 200..299) throw responseException(opened)
                accepted = true
                opened
            } finally {
                if (!accepted) release(opened)
            }
        }
        try {
            val session = SseSession(http)
            onOpen(session)
            http.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                readEvents(reader, session, onEvent)
            }
        } finally {
            release(http)
        }
    }

    private fun get(connection: HubConnection, path: String, authenticated: Boolean = true, group: RequestGroup? = null): String {
        val endpoint = HubTimingEndpoint.fromPath(path)
        val request = timing.nextRequest()
        val bodyBytes = BodyBytes()
        var status = 0
        return timing.measure(HubTimingStage.REQUEST, endpoint, request, { status }, { bodyBytes.value }) {
            val http = open(connection, path, authenticated, request = request, group = group)
            try {
                timing.measure(HubTimingStage.HEADERS, endpoint, request, status = { status }) { status = http.responseCode }
                if (status !in 200..299) throw responseException(http)
                // Refuse an announced oversized body before downloading it.
                if (http.contentLengthLong > maxResponseBytes) throw HubResponseTooLargeException()
                timing.measure(HubTimingStage.BODY, endpoint, request, { status }, { bodyBytes.value }) {
                    readBounded(http.inputStream, bodyBytes)
                }
            } finally {
                release(http, group)
            }
        }
    }

    private fun getOptional(connection: HubConnection, path: String, group: RequestGroup? = null): String? = try {
        get(connection, path, group = group)
    } catch (error: HubApiException) {
        // Optional details beyond the size cap must not stop the dashboard from connecting.
        if (error.statusCode == 404 || error is HubResponseTooLargeException) null else throw error
    }

    private fun open(
        connection: HubConnection,
        path: String,
        authenticated: Boolean,
        accept: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        request: Int = 0,
        group: RequestGroup? = null,
    ): HttpURLConnection {
        timing.measure(HubTimingStage.ROUTE, HubTimingEndpoint.fromPath(path), request) {
            HubAddressValidator.requireAllowed(connection)
            beforeRequest(connection)
        }
        val http = openHubConnection(URL(connection.baseUrl + path)).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 35_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Token-Monitor-Android/${BuildConfig.VERSION_NAME}")
            headers.forEach(::setRequestProperty)
            if (authenticated) setRequestProperty("Authorization", "Bearer ${connection.secret}")
        }
        synchronized(requests) {
            if (closed) {
                http.disconnect()
                throw CancellationException("Hub request cancelled")
            }
            requests.add(http)
        }
        try {
            group?.add(http)
        } catch (cancelled: CancellationException) {
            release(http)
            throw cancelled
        }
        return http
    }

    private fun responseException(http: HttpURLConnection): HubApiException = when (http.responseCode) {
        401 -> HubApiException(401, "The Hub rejected the secret. Check the connection settings.")
        404 -> HubApiException(404, "This Hub endpoint is not available. Update the desktop Token Monitor Hub.")
        else -> HubApiException(http.responseCode, "The Hub returned HTTP ${http.responseCode}. Try again when it is online.")
    }

    private suspend fun readEvents(reader: BufferedReader, session: SseSession, onEvent: suspend (HubStreamEvent) -> Unit) {
        val receiver = StatsStreamReceiver()
        val data = StringBuilder()
        var eventType = "message"
        while (!session.closed.get()) {
            val line = readSseLine(reader) ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    receiver.receive(HubStreamEvent(eventType, data.toString()))?.let { onEvent(it) }
                    data.clear()
                }
                eventType = "message"
            } else if (line.startsWith("event:")) {
                eventType = line.removePrefix("event:").trim().ifBlank { "message" }
            } else if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").trimStart()
                if (data.length + payload.length + (if (data.isEmpty()) 0 else 1) > maxSseEventChars) {
                    throw HubApiException(200, "The Hub stream event is too large for this phone dashboard.")
                }
                if (data.isNotEmpty()) data.append('\n')
                data.append(payload)
            }
        }
    }

    private fun readSseLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (true) {
            when (val character = reader.read()) {
                -1 -> return if (line.isEmpty()) null else line.toString()
                '\n'.code -> return line.toString()
                '\r'.code -> Unit
                else -> {
                    if (line.length >= maxSseLineChars) {
                        throw HubApiException(200, "The Hub stream line is too large for this phone dashboard.")
                    }
                    line.append(character.toChar())
                }
            }
        }
    }

    private fun readBounded(stream: InputStream, bodyBytes: BodyBytes? = null): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            received.addAndGet(read.toLong())
            bodyBytes?.let { it.value += read }
            totalBytes += read
            if (totalBytes > maxResponseBytes) {
                throw HubResponseTooLargeException()
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    internal class SseSession(private val connection: HttpURLConnection) : Closeable {
        internal val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) connection.disconnect()
        }
    }

    private class BodyBytes(var value: Long = 0)

    /** Owns only this snapshot's reads, so a failed batch cannot close another request. */
    private class RequestGroup : Closeable {
        private val active = mutableSetOf<HttpURLConnection>()
        private var closed = false

        fun add(http: HttpURLConnection) = synchronized(active) {
            if (closed) throw CancellationException("Hub snapshot ended")
            active.add(http)
        }

        fun remove(http: HttpURLConnection) = synchronized(active) { active.remove(http) }

        override fun close() {
            val pending = synchronized(active) {
                closed = true
                active.toList().also { active.clear() }
            }
            pending.forEach { it.disconnect() }
        }
    }

    private companion object {
        const val maxResponseBytes = 2 * 1024 * 1024
        // A stats response accepted by GET must also fit inside the stream's JSON envelope.
        const val maxSseEventChars = io.github.theminionooo.tokenmonitor.data.protocol.MAX_STREAM_EVENT_CHARS
        const val maxSseLineChars = maxSseEventChars + 6
    }
}

/** Cleartext private routes must never disclose their bearer secret to the system HTTP proxy. */
internal fun openHubConnection(url: URL): HttpURLConnection =
    (if (url.protocol.equals("http", ignoreCase = true)) url.openConnection(Proxy.NO_PROXY)
    else url.openConnection()) as HttpURLConnection
