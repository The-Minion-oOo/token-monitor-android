package io.github.theminionooo.tokenmonitor.data.network

import android.util.Log
import io.github.theminionooo.tokenmonitor.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

internal enum class HubTimingStage {
    ACTIVITY_CREATE, ACTIVITY_RESUME, ACTIVITY_PAUSE, DASHBOARD_OWNER, NETWORK_CHANGE,
    WORK_START, WORK_STOP, WORK_END, ROUTE_SELECTED, ATTEMPT_FAILED, RETRY_SCHEDULED, RETRY_WAIT,
    CANDIDATES, ROUTE, HEADERS, BODY, REQUEST, SNAPSHOT, CACHE_DECODE, DECODE, PUBLISH, STREAM_OPEN,
}
internal enum class HubTimingReason {
    NONE, OWNER_VISIBLE, OWNER_HIDDEN, MANUAL_REFRESH, NETWORK_AVAILABLE, NETWORK_LOST,
    MODE_CHANGE, VERIFY, DISCONNECT, CLOSE,
}
internal enum class HubTimingRoute {
    NONE, TAILSCALE, HOME, INTERNET_HTTPS;

    companion object {
        fun fromUrl(url: String): HubTimingRoute = when {
            HubAddressValidator.isTailscaleRoute(url) -> TAILSCALE
            HubAddressValidator.isLocalAddress(url) -> HOME
            url.startsWith("https://", ignoreCase = true) -> INTERNET_HTTPS
            else -> NONE
        }
    }
}
internal enum class HubTimingEndpoint {
    NONE, HEALTH, STATS, DEVICES, HISTORY, SUBSCRIPTIONS, STREAM, OTHER;

    companion object {
        fun fromPath(path: String): HubTimingEndpoint = when (path) {
            "/api/health" -> HEALTH
            "/api/stats" -> STATS
            "/api/devices" -> DEVICES
            "/api/history" -> HISTORY
            "/api/subscriptions" -> SUBSCRIPTIONS
            "/api/stats/stream" -> STREAM
            else -> OTHER
        }
    }
}
internal enum class HubTimingOutcome { OK, HTTP_ERROR, REJECTED, TOO_LARGE, CANCELLED, FAILED }

/** Fixed labels and numbers only: this type cannot hold a URL, credential, payload or error message. */
internal data class HubTimingSample(
    val stage: HubTimingStage,
    val endpoint: HubTimingEndpoint,
    val request: Int,
    val elapsedUs: Long,
    val durationUs: Long,
    val status: Int,
    val bytes: Long,
    val outcome: HubTimingOutcome,
    val run: Int = 0,
    val reason: HubTimingReason = HubTimingReason.NONE,
    val route: HubTimingRoute = HubTimingRoute.NONE,
    val attempt: Int = 0,
    val plannedMs: Long = 0,
) {
    fun line(): String = "stage=${stage.name.lowercase()} endpoint=${endpoint.name.lowercase()} request=$request " +
        "elapsed_us=$elapsedUs duration_us=$durationUs status=$status bytes=$bytes outcome=${outcome.name.lowercase()} " +
        "run=$run reason=${reason.name.lowercase()} route=${route.name.lowercase()} attempt=$attempt planned_ms=$plannedMs"
}

/** Opt-in local diagnostics. No clock reads or log formatting in the disabled path. */
internal class HubTimingRecorder(
    private val enabled: Boolean = BuildConfig.HUB_TIMING_ENABLED,
    private val clock: () -> Long = System::nanoTime,
    private val sink: (HubTimingSample) -> Unit = { Log.i("HubTiming", it.line()); Unit },
    private val maxEvents: Int = 512,
) {
    private val events = AtomicInteger()
    private val requests = AtomicInteger()
    private val runs = AtomicInteger()
    private val origin = if (enabled) clock() else 0L

    fun nextRequest(): Int = if (enabled) requests.incrementAndGet() else 0
    fun nextRun(): Int = if (enabled) runs.incrementAndGet() else 0

    fun mark(stage: HubTimingStage, run: Int = 0, reason: HubTimingReason = HubTimingReason.NONE,
             route: () -> HubTimingRoute = { HubTimingRoute.NONE }, attempt: Int = 0, plannedMs: Long = 0,
             outcome: HubTimingOutcome = HubTimingOutcome.OK) {
        if (!enabled || events.get() >= maxEvents) return
        val label = runCatching(route).getOrDefault(HubTimingRoute.NONE)
        record(stage, HubTimingEndpoint.NONE, 0, null, 0, 0, outcome, run, reason, label, attempt, plannedMs)
    }

    fun failure(error: Exception, run: Int, attempt: Int) {
        if (!enabled || events.get() >= maxEvents) return
        record(HubTimingStage.ATTEMPT_FAILED, HubTimingEndpoint.NONE, 0, null,
            (error as? HubApiException)?.statusCode ?: 0, 0, outcomeFor(error), run, attempt = attempt)
    }

    fun <T> measure(
        stage: HubTimingStage,
        endpoint: HubTimingEndpoint = HubTimingEndpoint.NONE,
        request: Int = 0,
        status: () -> Int = { 0 },
        bytes: () -> Long = { 0L },
        run: Int = 0,
        block: () -> T,
    ): T {
        if (!enabled || events.get() >= maxEvents) return block()
        val start = clock()
        var outcome = HubTimingOutcome.FAILED
        try {
            val result = block()
            outcome = HubTimingOutcome.OK
            return result
        } catch (error: Exception) {
            outcome = outcomeFor(error)
            throw error
        } finally {
            record(stage, endpoint, request, start, status(), bytes(), outcome, run)
        }
    }

    suspend fun <T> measureSuspending(stage: HubTimingStage, run: Int = 0, attempt: Int = 0,
                                     plannedMs: Long = 0, block: suspend () -> T): T {
        if (!enabled || events.get() >= maxEvents) return block()
        val start = clock()
        var outcome = HubTimingOutcome.FAILED
        try {
            val result = block()
            outcome = HubTimingOutcome.OK
            return result
        } catch (error: Exception) {
            outcome = outcomeFor(error)
            throw error
        } finally {
            record(stage, HubTimingEndpoint.NONE, 0, start, 0, 0, outcome, run, attempt = attempt, plannedMs = plannedMs)
        }
    }

    private fun outcomeFor(error: Exception): HubTimingOutcome = when (error) {
        is CancellationException -> HubTimingOutcome.CANCELLED
        is HubResponseTooLargeException -> HubTimingOutcome.TOO_LARGE
        is HubApiException -> if (error.statusCode == 0) HubTimingOutcome.REJECTED else HubTimingOutcome.HTTP_ERROR
        else -> HubTimingOutcome.FAILED
    }

    private fun record(stage: HubTimingStage, endpoint: HubTimingEndpoint, request: Int, start: Long?,
                       status: Int, bytes: Long, outcome: HubTimingOutcome, run: Int = 0,
                       reason: HubTimingReason = HubTimingReason.NONE, route: HubTimingRoute = HubTimingRoute.NONE,
                       attempt: Int = 0, plannedMs: Long = 0) {
        val end = clock()
        if (events.getAndIncrement() >= maxEvents) return
        val sample = HubTimingSample(stage, endpoint, request, (end - origin).coerceAtLeast(0) / 1000,
            start?.let { (end - it).coerceAtLeast(0) / 1000 } ?: 0,
            status.takeIf { it in 100..599 } ?: 0, bytes.coerceAtLeast(0), outcome,
            run.coerceAtLeast(0), reason, route, attempt.coerceAtLeast(0), plannedMs.coerceAtLeast(0))
        // Diagnostics must never change the result of a Hub read.
        runCatching { sink(sample) }
    }
}

internal object HubTimings {
    val recorder = HubTimingRecorder()
}
