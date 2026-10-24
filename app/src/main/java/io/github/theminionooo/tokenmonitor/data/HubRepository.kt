package io.github.theminionooo.tokenmonitor.data

import android.content.Context
import android.os.SystemClock
import io.github.theminionooo.tokenmonitor.data.network.BackoffPolicy
import io.github.theminionooo.tokenmonitor.data.network.EndpointFailover
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidation
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator
import io.github.theminionooo.tokenmonitor.data.network.HubApiClient
import io.github.theminionooo.tokenmonitor.data.network.HubApiException
import io.github.theminionooo.tokenmonitor.data.network.hubTlsMessage
import io.github.theminionooo.tokenmonitor.data.network.HubRouteGuard
import io.github.theminionooo.tokenmonitor.data.network.HubStreamEvent
import io.github.theminionooo.tokenmonitor.data.network.PhoneNetworks
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess
import io.github.theminionooo.tokenmonitor.data.network.prepareHomeAddressRepair
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SerialDiskQueue
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.domain.HubHistory
import io.github.theminionooo.tokenmonitor.domain.HubSubscriptions
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import io.github.theminionooo.tokenmonitor.data.network.HubNetworkObserver
import io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator
import io.github.theminionooo.tokenmonitor.widget.WidgetRefreshScheduler
import io.github.theminionooo.tokenmonitor.widget.WidgetRefreshDecision
import io.github.theminionooo.tokenmonitor.widget.widgetRefreshDecision
import io.github.theminionooo.tokenmonitor.widget.WidgetRuntime
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import io.github.theminionooo.tokenmonitor.data.network.widgetRefreshWifiAvailable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonObject

internal data class HubRepositoryState(
    val hasConnection: Boolean = false,
    /** The saved Hub base address, for display only. The secret never enters UI state. */
    val connectionUrl: String? = null,
    /** The optional home Wi-Fi address tried when [connectionUrl] does not answer. */
    val fallbackUrl: String? = null,
    /** Whichever saved address answered most recently. */
    val activeUrl: String? = null,
    val allowLocalNetwork: Boolean = false,
    val allowPublicHttps: Boolean = false,
    val localNetworkPermissionRequired: Boolean = false,
    val snapshot: HubSnapshot? = null,
    val refreshing: Boolean = false,
    val streamActive: Boolean = false,
    val widgetLiveActive: Boolean = false,
    val message: String? = null,
    /** The Hub answered 401; retrying cannot help until the pairing changes. */
    val secretRejected: Boolean = false,
)

internal fun retainDeviceHistory(streamed: HubStats, previous: List<DeviceUsage>): HubStats = streamed.copy(
    devices = streamed.devices.map { incoming ->
        val existing = previous.firstOrNull { it.id == incoming.id }
        if (existing != null && incoming.history.daily.isEmpty() && incoming.history.monthly.isEmpty()) {
            incoming.copy(
                historyAvailable = incoming.historyAvailable ?: existing.historyAvailable,
                history = existing.history,
            )
        } else {
            incoming
        }
    },
)

/**
 * The /history and /subscriptions bodies only change on a full reload, but every stream event and widget
 * poll rebuilds the snapshot. Keep their decoded form while the raw text is the same object.
 */
internal class DecodedBodies {
    private var historyRaw: String? = null
    private var history: HubHistory? = null
    private var subscriptionsRaw: String? = null
    private var subscriptions: HubSubscriptions? = null

    @Synchronized fun history(raw: String?): HubHistory? {
        if (raw == null) return null
        if (raw !== historyRaw) { history = HubProtocolParser.decodeHistory(raw); historyRaw = raw }
        return history
    }

    @Synchronized fun clear() {
        historyRaw = null; history = null; subscriptionsRaw = null; subscriptions = null
    }

    @Synchronized fun subscriptions(raw: String?): HubSubscriptions? {
        if (raw == null) return null
        if (raw !== subscriptionsRaw) { subscriptions = HubProtocolParser.decodeSubscriptions(raw); subscriptionsRaw = raw }
        return subscriptions
    }
}

/** Stream/poll updates reuse the latest device history rather than decoding the older /devices response. */
internal fun decodeStatsUpdate(
    wire: WireHubSnapshot,
    previousDevices: List<DeviceUsage>,
    bodies: DecodedBodies = DecodedBodies(),
    previousSnapshot: HubSnapshot? = null,
): HubSnapshot {
    val decoded = HubProtocolParser.decodeSnapshot(
        wire.health, wire.resolvedStats, null, wire.history, wire.subscriptions, wire.capturedAt,
        decodedHistory = bodies.history(wire.history), decodedSubscriptions = bodies.subscriptions(wire.subscriptions),
    )
    if (!decoded.health.ok || decoded.health.role != "hub") throw HubApiException(200, "The address is not a Token Monitor Hub.")
    val updated = decoded.copy(stats = retainDeviceHistory(decoded.stats, previousDevices))
    return previousSnapshot?.let { reuseSnapshotData(it, updated) } ?: updated
}

internal enum class HubWorkMode { Idle, WidgetPolling, DashboardStreaming }

internal const val WIDGET_POLL_INTERVAL_MS = 30_000L

internal fun selectHubWorkMode(
    dashboardVisible: Boolean,
    widgetActive: Boolean,
    widgetDeadline: Long? = null,
    elapsedRealtime: Long = 0,
): HubWorkMode = when {
    dashboardVisible -> HubWorkMode.DashboardStreaming
    widgetActive && (widgetDeadline == null || elapsedRealtime < widgetDeadline) -> HubWorkMode.WidgetPolling
    else -> HubWorkMode.Idle
}

/** Owns state on the main dispatcher; socket and disk work run on IO dispatchers. */
internal class HubRepository(context: Context, loadCache: (SnapshotCache) -> WireHubSnapshot? = { it.read() }) {
    private val appContext = context.applicationContext
    private val connectionStore = SecureConnectionStore(appContext)
    private val cache = SnapshotCache(appContext)
    private val decodedBodies = DecodedBodies()
    private val backoff = BackoffPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val diskQueue = SerialDiskQueue(diskScope)
    private val network = HubNetworkObserver(appContext) { scope.launch { if (workAllowed) refreshNow() } }
    private var connection: HubConnection? = connectionStore.read()
    private var activeUrl: String? = connection?.baseUrl
    private var lastWireSnapshot: WireHubSnapshot? = null
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HubRepositoryState> = _state.asStateFlow()
    private var pendingCacheWrite: WireHubSnapshot? = null
    private var lastCacheWriteAt = 0L
    private var lastCacheSave: Job? = null
    private var foregroundJob: Job? = null
    private var activeWorkMode = HubWorkMode.Idle
    private var subscriptionJob: Job? = null
    private val streamUpdates = StreamUpdateCoalescer()
    private val streamApplyLock = Mutex()
    private var streamFlushJob: Job? = null
    private var activeApi: HubApiClient? = null
    private val dashboardOwners = mutableSetOf<Any>()
    @Volatile private var dashboardVisible = false
    @Volatile private var widgetActive = false
    @Volatile private var widgetDeadline: Long? = null
    private val desiredWorkMode get() = selectHubWorkMode(dashboardVisible, widgetActive, widgetDeadline, SystemClock.elapsedRealtime())
    private val workAllowed get() = desiredWorkMode != HubWorkMode.Idle
    @Volatile private var generation = 0L
    private var lastSubscriptionAttemptAt = 0L
    private var attemptedSubscriptionVersion = ""
    private val widgetRefresh = WidgetRefreshPreferences(appContext)
    private var backgroundApi: HubApiClient? = null

    fun cancelWidgetBackgroundRefresh() {
        backgroundApi?.close()
    }

    /** Main-thread admission and generation checks prevent a scheduled read competing with visible work. */
    suspend fun refreshWidgetInBackground() {
        val settings = widgetRefresh.settings
        val decision = widgetRefreshDecision(settings.interval, WidgetUpdateCoordinator.hasWidgets(appContext),
            connection != null, widgetRefresh.record.secretRejected, dashboardVisible,
            WidgetRuntime.session.enabled || WidgetRuntime.session.refreshing || widgetActive,
            activeApi != null || backgroundApi != null)
        if (decision == WidgetRefreshDecision.Cancel) { WidgetRefreshScheduler.reconcileAsync(appContext); return }
        if (decision == WidgetRefreshDecision.Skip || (settings.wifiOnly && !widgetRefreshWifiAvailable(appContext))) return
        val saved = connection ?: return
        val token = generation
        val deadline = SystemClock.elapsedRealtime() + 45_000L
        val api = HubApiClient { route ->
            if (token != generation || SystemClock.elapsedRealtime() >= deadline || dashboardVisible || widgetActive ||
                widgetRefresh.settings != settings || widgetRefresh.record.secretRejected || !WidgetUpdateCoordinator.hasWidgets(appContext)) {
                throw CancellationException("Scheduled widget refresh ended")
            }
            if (settings.wifiOnly && !widgetRefreshWifiAvailable(appContext)) throw CancellationException("Wi-Fi unavailable")
            requireRouteAllowed(route)
        }
        backgroundApi = api
        val manager = appContext.getSystemService(android.net.ConnectivityManager::class.java)
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                if (settings.wifiOnly && !widgetRefreshWifiAvailable(appContext)) api.close()
            }
            override fun onLost(network: android.net.Network) {
                if (settings.wifiOnly) api.close()
            }
        }
        var observing = false
        var completed = false
        try {
            manager.registerDefaultNetworkCallback(callback)
            observing = true
            coroutineScope {
                // Socket IO is blocking: close it independently at the deadline, including a stuck read.
                val timeout = launch { delay(45_000); api.close() }
                try {
                    withTimeout(45_000) {
                        cacheLoadJob?.join()
                        val (url, wire) = withContext(Dispatchers.IO) {
                            EndpointFailover.run(permittedRoutes(saved, activeUrl)) {
                                api.loadWidgetSnapshot(saved.copy(baseUrl = it), lastWireSnapshot)
                            }
                        }
                        val snapshot = withContext(Dispatchers.IO) { parse(wire) }
                        ensureActive()
                        if (token == generation && connection === saved && !dashboardVisible && !widgetActive) {
                            activeUrl = url
                            applySuccess(wire, snapshot)
                            lastCacheSave?.join()
                            completed = true
                        }
                    }
                } finally { timeout.cancel() }
            }
        } catch (_: TimeoutCancellationException) {
            if (token == generation) widgetBackgroundFailure(false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (token == generation) widgetBackgroundFailure(error is HubApiException && error.statusCode == 401)
        } finally {
            api.close()
            if (observing) manager.unregisterNetworkCallback(callback)
            widgetRefresh.traffic(api.receivedBytes, completed)
            if (backgroundApi === api) backgroundApi = null
        }
    }

    private fun widgetBackgroundFailure(rejected: Boolean) {
        widgetRefresh.failure(System.currentTimeMillis(), rejected)
        if (rejected) _state.update { it.copy(secretRejected = true) }
        WidgetRefreshScheduler.reconcileAsync(appContext)
        diskQueue.enqueue { WidgetUpdateCoordinator.refreshSaved(appContext) }
    }

    internal val cacheLoadJob: Job? = connection?.let { saved ->
        scope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching { loadCache(cache)?.let { it to parse(it, cached = true) } }.getOrNull()
            } ?: return@launch
            // A network response or a pairing change always wins over a delayed disk read.
            if (connection === saved && lastWireSnapshot == null && _state.value.snapshot == null) {
                lastWireSnapshot = cached.first
                _state.update { it.copy(snapshot = cached.second) }
            }
        }
    }

    fun setDashboardVisible(owner: Any, visible: Boolean) {
        updateDashboardOwner(owner, visible)
        reconcileWork()
    }

    /** Reassert this screen's lifecycle state and restart once, even if its lease was lost. */
    fun refreshDashboard(owner: Any) {
        updateDashboardOwner(owner, true)
        reconcileWork(forceRefresh = true)
    }

    private fun updateDashboardOwner(owner: Any, visible: Boolean) {
        if (visible) dashboardOwners.add(owner) else dashboardOwners.remove(owner)
        dashboardVisible = dashboardOwners.isNotEmpty()
    }

    fun setWidgetActive(active: Boolean, deadlineElapsedRealtime: Long? = null) {
        widgetActive = active
        widgetDeadline = if (active) deadlineElapsedRealtime else null
        reconcileWork()
    }

    private fun reconcileWork(forceRefresh: Boolean = false) {
        _state.update { it.copy(localNetworkPermissionRequired = needsLocalPermission()) }
        val desired = desiredWorkMode
        if (desired == HubWorkMode.Idle) {
            network.stop()
            stopForegroundWork()
            return
        }
        network.start()
        if (forceRefresh || foregroundJob?.isActive != true || activeWorkMode != desired) {
            stopForegroundWork()
            startForegroundWork(desired)
        }
    }

    fun refreshNow() {
        if (connection == null || !workAllowed) return
        reconcileWork(forceRefresh = true)
    }

    suspend fun validateAndSave(rawUrl: String, rawFallbackUrl: String, secret: String, allowLocalNetwork: Boolean, allowPublicHttps: Boolean = false): String? {
        val primary = when (val result = HubAddressValidator.validate(rawUrl, secret, allowLocalNetwork, allowPublicHttps)) {
            is HubAddressValidation.Allowed -> result.connection
            is HubAddressValidation.Rejected -> return result.reason
        }
        val fallback = if (rawFallbackUrl.isBlank()) null else {
            when (val result = HubAddressValidator.validate(rawFallbackUrl, secret, true)) {
                is HubAddressValidation.Allowed -> result.connection.baseUrl
                is HubAddressValidation.Rejected -> return "Home Wi-Fi address: ${result.reason}"
            }
        }
        val candidate = primary.copy(fallbackUrl = fallback?.takeIf { it != primary.baseUrl })
        return verifyAndSave(candidate)
    }

    suspend fun repairHomeAddress(rawAddress: String): String? {
        val saved = connection
        val candidate = when (val result = prepareHomeAddressRepair(saved, rawAddress)) {
            is HubAddressValidation.Allowed -> result.connection
            is HubAddressValidation.Rejected -> return result.reason
        }
        // Test the address the person selected; a reachable primary must not conceal a bad repair.
        return verifyAndSave(candidate, candidate.fallbackUrl ?: candidate.baseUrl)
    }

    private suspend fun verifyAndSave(candidate: HubConnection, testAddress: String? = null): String? {
        stopForegroundWork()
        val token = generation
        val api = HubApiClient { route ->
            if (generation != token) throw CancellationException("Connection check cancelled")
            requireRouteAllowed(route)
        }.also { activeApi = it }
        return try {
            val (url, wire) = withContext(Dispatchers.IO) {
                val routes = testAddress?.let(::listOf) ?: permittedRoutes(candidate, null)
                EndpointFailover.run(routes) { api.loadSnapshot(candidate.copy(baseUrl = it)) }
            }
            coroutineContext.ensureActive()
            if (generation != token) return "Connection check cancelled. Try again."
            val snapshot = withContext(Dispatchers.IO) { parse(wire) }
            if (generation != token) return "Connection check cancelled. Try again."
            connectionStore.save(candidate)
            connection = candidate
            activeUrl = url
            applySuccess(wire, snapshot)
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: HubApiException) { error.presentation }
        catch (error: Exception) { hubTlsMessage(error) ?: "The Hub could not be verified. Check the address and try again." }
        finally { api.close(); if (activeApi === api) activeApi = null }
    }

    /** Revoking internet consent takes effect without a request or re-entering the secret. */
    fun disablePublicHttps() {
        val saved = connection?.takeIf { it.allowPublicHttps } ?: return
        val privateOnly = saved.copy(allowPublicHttps = false)
        connectionStore.save(privateOnly)
        stopForegroundWork()
        connection = privateOnly
        _state.update { it.copy(allowPublicHttps = false, message = "Internet Hub access is off. Use Tailscale or update the connection settings.") }
        reconcileWork()
    }

    fun disconnect() {
        cacheLoadJob?.cancel()
        stopForegroundWork()
        connection = null
        activeUrl = null
        lastWireSnapshot = null
        pendingCacheWrite = null
        decodedBodies.clear()
        connectionStore.clear()
        widgetRefresh.clearRecord()
        WidgetRefreshScheduler.reconcileAsync(appContext)
        diskQueue.enqueue { cache.clear(); WidgetUpdateCoordinator.refresh(appContext) }
        _state.value = HubRepositoryState()
    }

    fun close() {
        network.stop()
        stopForegroundWork()
        scope.cancel()
        diskQueue.closeWhenIdle()
    }

    private fun initialState(): HubRepositoryState = HubRepositoryState(
        hasConnection = connection != null,
        connectionUrl = connection?.baseUrl,
        fallbackUrl = connection?.fallbackUrl,
        activeUrl = activeUrl,
        allowLocalNetwork = connection?.allowLocalNetwork ?: false,
        allowPublicHttps = connection?.allowPublicHttps ?: false,
        localNetworkPermissionRequired = needsLocalPermission(),
    )

    private fun startForegroundWork(mode: HubWorkMode = desiredWorkMode) {
        val saved = connection ?: return
        if (mode == HubWorkMode.Idle || mode != desiredWorkMode || foregroundJob?.isActive == true) return
        val token = generation
        val api = HubApiClient { route ->
            if (generation != token || desiredWorkMode != mode) throw CancellationException("Hub work ended")
            requireRouteAllowed(route)
        }.also { activeApi = it }
        activeWorkMode = mode
        foregroundJob = scope.launch {
            var attempt = 0
            try {
                while (desiredWorkMode == mode && generation == token) {
                    var connected = false
                    try {
                        _state.update { it.copy(refreshing = true, message = null, secretRejected = false) }
                        val (url, wire) = withContext(Dispatchers.IO) {
                            EndpointFailover.run(permittedRoutes(saved, activeUrl)) { api.loadSnapshot(saved.copy(baseUrl = it)) }
                        }
                        ensureActive()
                        if (generation != token) break
                        val snapshot = withContext(Dispatchers.IO) { parse(wire) }
                        if (generation != token) break
                        activeUrl = url
                        applySuccess(wire, snapshot)
                        connected = true
                        when (mode) {
                            HubWorkMode.DashboardStreaming -> try {
                                withContext(Dispatchers.IO) {
                                    api.streamStats(saved.copy(baseUrl = url),
                                        onOpen = { withContext(Dispatchers.Main.immediate) {
                                            if (generation == token) _state.update { it.copy(streamActive = true, widgetLiveActive = false) }
                                        } },
                                        onEvent = { event -> withContext(Dispatchers.Main.immediate) {
                                            if (generation == token) {
                                                val ready = streamUpdates.offer(event, SystemClock.elapsedRealtime())
                                                if (applyStreamEvents(ready, token)) attempt = 0
                                                scheduleStreamFlush(saved, api, token)
                                                refreshSubscriptions(saved, api, token)
                                            }
                                        } },
                                    )
                                }
                            } finally {
                                // A reconnect starts with a complete snapshot; an update held from this stream is stale.
                                cancelStreamFlush()
                            }
                            HubWorkMode.WidgetPolling -> {
                                _state.update { it.copy(streamActive = false, widgetLiveActive = true) }
                                while (desiredWorkMode == mode && generation == token) {
                                    delay(WIDGET_POLL_INTERVAL_MS)
                                    if (generation != token || desiredWorkMode != mode) break
                                    val raw = withContext(Dispatchers.IO) {
                                        api.getStats(saved.copy(baseUrl = url))
                                    }
                                    ensureActive()
                                    if (generation != token || desiredWorkMode != mode) break
                                    if (applyStatsUpdate(raw, token)) attempt = 0
                                    refreshSubscriptions(saved, api, token)
                                }
                            }
                            HubWorkMode.Idle -> Unit
                        }
                        if (generation == token) _state.update { it.copy(streamActive = false, widgetLiveActive = false, message = "Live updates paused. Retrying...") }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (generation != token) break
                        val rejected = error is HubApiException && error.statusCode == 401
                        if (rejected) {
                            widgetRefresh.failure(System.currentTimeMillis(), true)
                            WidgetRefreshScheduler.reconcileAsync(appContext)
                        }
                        _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false, secretRejected = rejected,
                            message = if (error is HubApiException) error.presentation else hubTlsMessage(error) ?: "Hub unavailable. Showing the last successful snapshot; retrying...") }
                        if (rejected) break
                        // A dropped stream on a route that just answered is retried there first; only a failed
                        // connection moves the other saved address to the front.
                        if (!connected) activeUrl = EndpointFailover.candidates(saved, activeUrl).getOrNull(1) ?: activeUrl
                    }
                    delay(backoff.delayForAttempt(attempt++))
                }
            } finally {
                api.close()
                if (generation == token) {
                    activeWorkMode = HubWorkMode.Idle
                    _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false) }
                }
            }
        }
    }

    private fun stopForegroundWork() {
        cancelWidgetBackgroundRefresh()
        generation++
        activeApi?.close()
        activeApi = null
        foregroundJob?.cancel()
        foregroundJob = null
        activeWorkMode = HubWorkMode.Idle
        subscriptionJob?.cancel()
        subscriptionJob = null
        cancelStreamFlush()
        _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false) }
        pendingCacheWrite?.let { persist(it, force = true) }
    }

    private fun persist(wire: WireHubSnapshot, force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastCacheWriteAt >= 60_000L) {
            pendingCacheWrite = null
            lastCacheWriteAt = now
            lastCacheSave = diskQueue.enqueue {
                cache.save(wire)
                WidgetUpdateCoordinator.refreshSaved(appContext)
            }
        } else pendingCacheWrite = wire
    }

    private fun parse(wire: WireHubSnapshot, cached: Boolean = false): HubSnapshot = HubProtocolParser.decodeSnapshot(
        wire.health, wire.resolvedStats, wire.devices, wire.history, wire.subscriptions, wire.capturedAt, cached,
    ).also { if (!it.health.ok || it.health.role != "hub") throw HubApiException(200, "The address is not a Token Monitor Hub.") }

    private fun needsLocalPermission(): Boolean = connection?.let {
        LocalNetworkAccess.needed(it) && !LocalNetworkAccess.granted(appContext)
    } ?: false

    /** Runs before every request, including the unauthenticated identity check, on an IO thread. */
    private fun requireRouteAllowed(route: HubConnection) {
        if (HubAddressValidator.isLocalAddress(route.baseUrl) && !LocalNetworkAccess.granted(appContext)) {
            throw HubApiException(0, LocalNetworkAccess.deniedMessage)
        }
        HubRouteGuard.require(route.baseUrl, PhoneNetworks.read(appContext), route.allowPublicHttps)
    }

    private fun permittedRoutes(saved: HubConnection, preferred: String?): List<String> {
        val allowed = LocalNetworkAccess.allowedRoutes(saved, preferred, LocalNetworkAccess.granted(appContext)).ifEmpty {
            throw HubApiException(0, LocalNetworkAccess.deniedMessage)
        }
        val networks = PhoneNetworks.read(appContext)
        return allowed.filter { HubRouteGuard.usable(it, networks) }.ifEmpty {
            throw HubApiException(0, HubRouteGuard.unavailableMessage(allowed, networks))
        }
    }

    private fun applySuccess(wire: WireHubSnapshot, snapshot: HubSnapshot) {
        widgetRefresh.success(wire.capturedAt)
        WidgetRefreshScheduler.reconcileAsync(appContext)
        lastWireSnapshot = wire
        persist(wire, true)
        _state.value = HubRepositoryState(
            hasConnection = connection != null, connectionUrl = connection?.baseUrl,
            fallbackUrl = connection?.fallbackUrl, activeUrl = activeUrl,
            allowLocalNetwork = connection?.allowLocalNetwork ?: false,
            allowPublicHttps = connection?.allowPublicHttps ?: false, snapshot = snapshot,
            localNetworkPermissionRequired = needsLocalPermission(),
        )
    }

    /** The held update and a newly arrived one never decode at the same time. */
    private suspend fun applyStreamEvents(events: List<HubStreamEvent>, token: Long): Boolean = streamApplyLock.withLock {
        var applied = false
        events.forEach { if (applyStatsUpdate(it.data, token, streamEventType = it.type)) applied = true }
        applied
    }

    private fun scheduleStreamFlush(saved: HubConnection, api: HubApiClient, token: Long) {
        if (streamFlushJob?.isActive == true) return
        val wait = streamUpdates.flushDelay(SystemClock.elapsedRealtime()) ?: return
        streamFlushJob = scope.launch {
            delay(wait)
            if (generation != token) return@launch
            applyStreamEvents(streamUpdates.drain(SystemClock.elapsedRealtime()), token)
            refreshSubscriptions(saved, api, token)
            // An update that arrived while this one was decoding is held; schedule it before finishing.
            streamFlushJob = null
            scheduleStreamFlush(saved, api, token)
        }
    }

    private fun cancelStreamFlush() {
        streamFlushJob?.cancel()
        streamFlushJob = null
        streamUpdates.clear()
    }

    private suspend fun applyStatsUpdate(raw: String, token: Long, streamEventType: String? = null): Boolean {
        val previous = _state.value.snapshot ?: return false
        val wire = lastWireSnapshot ?: return false
        val parsed = withContext(Dispatchers.IO) {
            if (streamEventType != null && streamEventType !in setOf("message", "snapshot", "stats", "freshness")) {
                return@withContext null
            }
            runCatching {
                val capturedAt = System.currentTimeMillis()
                if (streamEventType == "freshness") {
                    val patch = HubStreamProtocol.decodeFreshness(raw)
                    val reduced = HubStreamProtocol.applyFreshness(previous, patch, capturedAt)
                    if (reduced != null) {
                        val accumulated = HubStreamProtocol.mergeFreshness(wire.freshness ?: JsonObject(emptyMap()), patch)
                        return@runCatching wire.copy(capturedAt = capturedAt, freshness = accumulated) to reduced
                    }
                }
                // The decoder accepts complete bodies and SSE envelopes. Keep the original text
                // until persistence instead of parsing, stringifying and parsing it again here.
                val stats = if (streamEventType == "freshness") HubStreamProtocol.mergeFreshness(wire.resolvedStats, raw) else raw
                val updatedWire = wire.copy(stats = stats, capturedAt = capturedAt, freshness = null)
                updatedWire to decodeStatsUpdate(updatedWire, previous.stats.devices, decodedBodies, previous)
            }.getOrNull()
        } ?: return false
        if (generation != token) return false
        // A subscription request may complete while this event is being parsed.
        val updatedWire = parsed.first.copy(subscriptions = lastWireSnapshot?.subscriptions)
        val next = parsed.second.copy(subscriptions = _state.value.snapshot?.subscriptions ?: parsed.second.subscriptions)
        lastWireSnapshot = updatedWire
        _state.update { it.copy(snapshot = next, message = null) }
        persist(updatedWire, false)
        return true
    }

    private fun refreshSubscriptions(saved: HubConnection, api: HubApiClient, token: Long) {
        val snapshot = _state.value.snapshot ?: return
        val version = snapshot.stats.subscriptionsUpdatedAt
        if (version.isBlank() || version == snapshot.subscriptions.updatedAt || subscriptionJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (version == attemptedSubscriptionVersion && now - lastSubscriptionAttemptAt < 60_000L) return
        attemptedSubscriptionVersion = version
        lastSubscriptionAttemptAt = now
        val route = activeUrl ?: saved.baseUrl
        subscriptionJob = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { api.getSubscriptions(saved.copy(baseUrl = route)) }
                ensureActive()
                if (generation != token) return@launch
                val current = _state.value.snapshot ?: return@launch
                val subscriptions = HubProtocolParser.decodeSubscriptions(raw)
                _state.update { it.copy(snapshot = current.copy(subscriptions = subscriptions)) }
                lastWireSnapshot?.copy(subscriptions = raw)?.let { lastWireSnapshot = it; persist(it, true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the last good list; retry the same version after a minute. */ }
        }
    }

}
