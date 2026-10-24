package io.github.theminionooo.tokenmonitor.widget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshInterval
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import java.util.concurrent.Executors

internal enum class WidgetRefreshDecision { Cancel, Skip, Run }

internal fun widgetRefreshDecision(
    interval: WidgetRefreshInterval, widgets: Boolean, paired: Boolean, rejected: Boolean,
    dashboard: Boolean = false, session: Boolean = false, checking: Boolean = false,
): WidgetRefreshDecision = when {
    interval == WidgetRefreshInterval.Off || !widgets || !paired || rejected -> WidgetRefreshDecision.Cancel
    dashboard || session || checking -> WidgetRefreshDecision.Skip
    else -> WidgetRefreshDecision.Run
}

internal object WidgetRefreshScheduler {
    const val JOB_ID = 0x544d02
    private val queue = Executors.newSingleThreadExecutor { task -> Thread(task, "widget-schedule").apply { isDaemon = true } }

    fun reconcileAsync(context: Context) {
        val app = context.applicationContext
        queue.execute { reconcile(app) }
    }

    @Synchronized fun reconcile(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val prefs = WidgetRefreshPreferences(context)
        val settings = prefs.settings
        if (settings.interval == WidgetRefreshInterval.Off || !WidgetUpdateCoordinator.hasWidgets(context) || prefs.record.secretRejected) {
            scheduler.cancel(JOB_ID)
            return
        }
        val decision = widgetRefreshDecision(settings.interval, WidgetUpdateCoordinator.hasWidgets(context),
            SecureConnectionStore(context).read() != null, prefs.record.secretRejected)
        if (decision == WidgetRefreshDecision.Cancel) {
            scheduler.cancel(JOB_ID)
            return
        }
        val period = settings.interval.minutes * 60_000L
        // Wi-Fi transport is checked again at each request, including VPN underlay checks.
        val network = if (settings.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        val existing = scheduler.getPendingJob(JOB_ID)
        @Suppress("DEPRECATION") // The network-type API is available on the minimum supported API 26.
        if (existing?.isPeriodic == true && existing.intervalMillis == period &&
            existing.networkType == network && existing.isPersisted) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, WidgetRefreshJobService::class.java))
            .setPeriodic(period, period / 3).setRequiredNetworkType(network).setPersisted(true).build())
    }
}
