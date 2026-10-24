package io.github.theminionooo.tokenmonitor.widget

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.theminionooo.tokenmonitor.data.HubRepository
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import kotlinx.coroutines.*

/** A bounded, silent read. Android owns scheduling and may defer it while the phone sleeps. */
class WidgetRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var work: Job? = null
    private var repository: HubRepository? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val previous = work
        work = scope.launch {
            previous?.join()
            ensureActive()
            val hub = HubRepositoryPool.acquire(applicationContext)
            repository = hub
            try {
                hub.refreshWidgetInBackground()
            } catch (_: CancellationException) {
                // Local admission may end a run without JobScheduler cancelling this coroutine.
            } finally {
                if (isActive) jobFinished(params, false)
                repository = null
                HubRepositoryPool.release(hub)
            }
        }
        return true
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
