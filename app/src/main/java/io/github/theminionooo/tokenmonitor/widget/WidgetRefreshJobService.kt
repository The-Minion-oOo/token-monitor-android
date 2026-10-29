package io.github.theminionooo.tokenmonitor.widget

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import io.github.theminionooo.tokenmonitor.data.network.widgetRefreshWifiAvailable
import kotlinx.coroutines.*

/** A bounded, silent read. Android owns scheduling and may defer it while the phone sleeps. */
class WidgetRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null
    private var repository: HubRepository? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (admission() != WidgetRefreshDecision.Run) return false
        val previous = work
        work = scope.launch {
            previous?.join()
            ensureActive()
            try {
                runAdmittedWidgetRefresh(::admission,
                    acquire = { HubRepositoryPool.acquire(applicationContext).also { repository = it } },
                    release = { repository = null; HubRepositoryPool.release(it) },
                    refresh = { it.refreshWidgetInBackground() })
            } catch (_: CancellationException) {
                // Local admission may end a run without JobScheduler cancelling this coroutine.
            } finally {
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    private fun admission(): WidgetRefreshDecision {
        val prefs = WidgetRefreshPreferences(applicationContext)
        val settings = prefs.settings
        return widgetRefreshAdmission(settings,
            widgets = { WidgetUpdateCoordinator.hasWidgets(applicationContext) },
            rejected = { prefs.record.secretRejected },
            busy = { WidgetRuntime.session.enabled || WidgetRuntime.session.refreshing || HubRepositoryPool.backgroundRefreshBusy },
            wifiAvailable = { widgetRefreshWifiAvailable(applicationContext) },
        ).also { if (it == WidgetRefreshDecision.Cancel) WidgetRefreshScheduler.reconcileAsync(applicationContext) }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        repository?.cancelWidgetBackgroundRefresh()
        work?.cancel()
        return false // The next periodic window supplies the next attempt.
    }

    override fun onDestroy() {
        repository?.cancelWidgetBackgroundRefresh()
        scope.cancel()
        super.onDestroy()
    }
}
