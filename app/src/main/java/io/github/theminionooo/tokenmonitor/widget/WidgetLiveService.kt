package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.ui.appText
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.snapshotDate
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import io.github.theminionooo.tokenmonitor.ui.formatBoundary
import io.github.theminionooo.tokenmonitor.ui.formatDuration
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.windowTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

internal data class WidgetSession(
    val enabled: Boolean = false,
    val refreshing: Boolean = false,
    val connected: Boolean = false,
    val expiresAt: Long = 0,
    val snapshot: HubSnapshot? = null,
    val note: String? = null,
)

/** Deliberately process-local: a killed process never advertises a surviving Live session. */
internal object WidgetRuntime {
    @Volatile var session = WidgetSession()
}

internal data class WidgetNotificationContent(
    val title: String,
    val headline: String,
    val detail: String,
    val subtext: String?,
    val shortText: String,
)

/** Notification identity is based on visible content, never a fetch timestamp. */
internal fun widgetNotificationContent(
    session: WidgetSession,
    live: Boolean,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = io.github.theminionooo.tokenmonitor.ui.AppLanguage.locale(),
    currency: io.github.theminionooo.tokenmonitor.data.storage.UsageCurrency = io.github.theminionooo.tokenmonitor.data.storage.UsageCurrency(),
    displayOptions: io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions = io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions(),
): WidgetNotificationContent {
    val snapshot = session.snapshot
    val connected = session.connected && snapshot?.fromCache != true && session.note == null
    val clock = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zoneId)
    val headline = snapshot?.let {
        val totals = appText(R.string.ui_1_s_tokens_2_s_8aebf, "%1\$s tokens · %2\$s", widgetTokens(it.today.totalTokens), formatUsageCost(it.today.costUsd, it.today.unpricedTokens, currency = currency))
        val day = snapshotDate(it, zoneId)
        when {
            !connected -> appText(R.string.ui_saved_1_s_432ed, "Saved · %1\$s", totals)
            day == Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate() -> appText(R.string.ui_1_s_today_4398d, "%1\$s today", totals)
            day != null -> appText(R.string.ui_1_s_for_2_s_c9074, "%1\$s for %2\$s", totals, day)
            else -> totals
        }
    }
        ?: if (live) appText(R.string.ui_waiting_for_the_hub_43049, "Waiting for the Hub") else appText(R.string.ui_fetching_a_fresh_hub_snapshot_cda7e, "Fetching a fresh Hub snapshot")
    val lastUpdated = snapshot?.takeUnless { connected }?.let {
        if (it.capturedAt <= 0) appText(R.string.ui_last_update_time_unavailable_fac67, "Last update time unavailable") else {
            val age = (now - it.capturedAt).coerceAtLeast(0L)
            appText(R.string.ui_last_updated_cb5f4, "Last updated ") + if (age < 60_000) appText(R.string.ui_less_than_a_minute_ago_abff0, "less than a minute ago") else appText(R.string.ui_1_s_ago_1d3b1, "%1\$s ago", formatDuration(age / 60_000 * 60_000))
        }
    }
    val windows = snapshot?.let { quotaRows(it, displayOptions) }.orEmpty().joinToString("\n") { row ->
        val reset = formatBoundary(row.window.resetsAt, row.window.boundaryKind, now)
        appText(R.string.ui_1_s_2_s_3_s_left_f1c0b, "%1\$s %2\$s %3\$s%% left", row.provider.providerLabel(), windowTitle(row.window, row.siblings), row.remainingPercent.toInt()) +
            if (reset.isNotBlank()) " · $reset" else ""
    }
    val until = when {
        live && session.expiresAt > 0 -> "${if (connected) "Live until" else "Session ends"} ${clock.format(Instant.ofEpochMilli(session.expiresAt))}"
        live -> appText(R.string.ui_widget_updates_for_one_hour_f38cd, "Widget updates for one hour")
        else -> null
    }
    return WidgetNotificationContent(
        title = when {
            !live -> appText(R.string.ui_refreshing_token_monitor_82285, "Refreshing Token Monitor")
            connected -> appText(R.string.ui_token_monitor_live_ef514, "Token Monitor · Live")
            snapshot != null -> appText(R.string.ui_token_monitor_reconnecting_23229, "Token Monitor · Reconnecting")
            else -> appText(R.string.ui_token_monitor_connecting_c6cd1, "Token Monitor · Connecting")
        },
        headline = headline,
        detail = listOfNotNull(headline, lastUpdated, windows.ifBlank { null }, until).joinToString("\n"),
        subtext = until,
        shortText = when {
            connected && snapshot != null -> formatCompactTokens(snapshot.today.totalTokens)
            snapshot != null -> appText(R.string.ui_offline_e01fa, "Offline")
            else -> appText(R.string.ui_waiting_33d30, "Waiting")
        },
    )
}

/** Started only by a visible, explicit widget action. No restart, boot receiver, or wake lock. */
class WidgetLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repository: HubRepository? = null
    private var collection: Job? = null
    private var expiry: Job? = null
    private var deadline: WidgetSessionDeadline? = null
    private var observingWake = false
    private var live = false
    private var finishing = false
    private var shownNotification: WidgetNotificationContent? = null
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON && !expireIfNeeded()) showNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, appText(R.string.ui_widget_live_updates_e64fb, "Widget live updates"), NotificationManager.IMPORTANCE_LOW))
        if (intent?.action !in listOf(ACTION_LIVE, ACTION_REFRESH) || finishing) {
            // Every start arrives through startForegroundService, which Android only accepts once
            // startForeground has run, even when this start ends straight away.
            runCatching { enterForeground(notificationContent(WidgetRuntime.session, false)); stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // An hour that ran out while the phone slept ends that session; this tap starts a new one.
        if (deadline?.hasExpired(SystemClock.elapsedRealtime()) == true) {
            deadline = null
            live = false
        }
        current = this
        val alreadyLive = live
        live = live || intent?.action == ACTION_LIVE
        val activeDeadline = widgetSessionDeadline(deadline, alreadyLive, live, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        deadline = activeDeadline
        WidgetRuntime.session = WidgetRuntime.session.copy(enabled = live, refreshing = true, expiresAt = activeDeadline.displayExpiresAt, note = null)
        try {
            val content = notificationContent(WidgetRuntime.session, live)
            shownNotification = content
            enterForeground(content)
        } catch (_: RuntimeException) {
            finishSession(appText(R.string.ui_live_unavailable_tap_to_retry_e2d5b, "Live unavailable. Tap to retry."))
            return START_NOT_STICKY
        }
        val hub = repository ?: HubRepositoryPool.acquire(this).also { repository = it }
        if (!hub.state.value.hasConnection) {
            finishSession(appText(R.string.ui_open_the_app_to_connect_your_hub_cd8a1, "Open the app to connect your Hub"))
            return START_NOT_STICKY
        }
        collection?.cancel()
        val before = hub.state.value.snapshot
        hub.setWidgetActive(true, activeDeadline.elapsedRealtime)
        if (intent?.action == ACTION_REFRESH) hub.refreshNow()
        collection = scope.launch {
            hub.state.collectLatest { state ->
                if (expireIfNeeded()) return@collectLatest
                if (!state.hasConnection) {
                    finishSession(appText(R.string.ui_connect_your_hub_in_the_app_9425c, "Connect your Hub in the app"))
                    return@collectLatest
                }
                // The repository has stopped retrying a rejected secret, so there is nothing left to wait for.
                if (state.secretRejected) {
                    finishSession(appText(R.string.ui_hub_rejected_the_secret_open_the_app_c1cae, "Hub rejected the secret · open the app"))
                    return@collectLatest
                }
                WidgetRuntime.session = WidgetRuntime.session.copy(
                    connected = state.streamActive || state.widgetLiveActive, refreshing = state.refreshing,
                    snapshot = state.snapshot, note = if (state.message != null) appText(R.string.ui_hub_offline_retrying_a9d88, "Hub offline · retrying") else null,
                )
                withContext(Dispatchers.IO) { WidgetUpdateCoordinator.refresh(this@WidgetLiveService) }
                showNotification()
                if (!live && ((state.snapshot !== before && state.snapshot?.fromCache == false) || state.message != null)) {
                    finishSession(if (state.message != null) appText(R.string.ui_refresh_failed_showing_saved_data_bb1cc, "Refresh failed · showing saved data") else null)
                }
            }
        }
        if (!observingWake && !finishing) {
            ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
            observingWake = true
        }
        expiry?.cancel()
        expiry = scope.launch {
            while (!finishing && !expireIfNeeded()) {
                // Handler delays pause in deep sleep. Recheck elapsed time on each callback;
                // the repository independently checks this same deadline before network work.
                showNotification()
                delay(minOf(activeDeadline.remainingMillis(SystemClock.elapsedRealtime()), EXPIRY_CHECK_MS))
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(content: WidgetNotificationContent) {
        val notification = notification(content)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun expireIfNeeded(): Boolean {
        if (deadline?.hasExpired(SystemClock.elapsedRealtime()) != true) return false
        finishSession(if (live) appText(R.string.ui_live_session_ended_e4637, "Live session ended") else appText(R.string.ui_refresh_timed_out_c439a, "Refresh timed out"))
        return true
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        finishSession(appText(R.string.ui_android_paused_live_tap_to_retry_a217f, "Android paused Live. Tap to retry."))
    }

    /**
     * The notification that keeps the service alive doubles as the lock-screen and status-bar view of
     * the session: the figure and the tightest windows, refreshed whenever the snapshot changes. On
     * Android 16 it asks to be promoted, which is what puts it on the lock screen and in the status
     * bar chip for the hour it runs.
     */
    private fun notificationContent(session: WidgetSession, live: Boolean): WidgetNotificationContent {
        val options = io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences(this).options.value
        return widgetNotificationContent(session, live, currency = io.github.theminionooo.tokenmonitor.data.storage.usageCurrency(options), displayOptions = options)
    }

    private fun notification(content: WidgetNotificationContent = notificationContent(WidgetRuntime.session, live)): Notification {
        val stop = PendingIntent.getBroadcast(this, 21, Intent(this, WidgetStopReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 22, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_widget_notification)
            .setContentTitle(content.title)
            .setContentText(content.headline)
            .setStyle(Notification.BigTextStyle().bigText(content.detail))
            .setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, appText(R.string.ui_stop_9e253, "Stop"), stop).build())
        content.subtext?.let(builder::setSubText)
        // Promotion (lock screen, status bar chip) arrived in the Android 16 minor release, so check the full version.
        if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(content.shortText)
        if (Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) builder.setRequestPromotedOngoing(true)
        return builder.build()
    }

    /** Re-posts the notification only when its text would change, so the shade never flickers. */
    private fun showNotification() {
        val content = notificationContent(WidgetRuntime.session, live)
        if (content == shownNotification || finishing) return
        shownNotification = content
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(content)) }
    }

    private fun finishSession(note: String? = null) {
        if (finishing) return
        finishing = true
        collection?.cancel()
        expiry?.cancel()
        if (observingWake) {
            unregisterReceiver(wakeReceiver)
            observingWake = false
        }
        repository?.setWidgetActive(false)
        WidgetRuntime.session = WidgetRuntime.session.copy(enabled = false, refreshing = false, connected = false, expiresAt = 0, note = note)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        refreshAsync(applicationContext)
    }

    override fun onDestroy() {
        finishSession()
        scope.cancel()
        repository?.let(HubRepositoryPool::release)
        repository = null
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        internal const val ACTION_LIVE = "widget.LIVE"
        internal const val ACTION_REFRESH = "widget.REFRESH"
        internal const val SESSION_MS = 60 * 60 * 1000L
        internal const val REFRESH_MS = 45_000L
        private const val EXPIRY_CHECK_MS = 30_000L
        private const val CHANNEL = "widget_live"
        private const val NOTIFICATION_ID = 71
        private var current: WidgetLiveService? = null

        internal fun stop(context: Context) {
            current?.finishSession()
            context.stopService(Intent(context, WidgetLiveService::class.java))
            WidgetRuntime.session = WidgetSession()
            refreshAsync(context.applicationContext)
        }

        private fun refreshAsync(context: Context) {
            kotlin.concurrent.thread(name = "widget-status") { WidgetUpdateCoordinator.refresh(context) }
        }
    }
}
