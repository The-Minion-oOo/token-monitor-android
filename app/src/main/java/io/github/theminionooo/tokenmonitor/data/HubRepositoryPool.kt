package io.github.theminionooo.tokenmonitor.data

import android.content.Context

/** Main-thread owners share one connection while either the dashboard or widget needs it. */
internal object HubRepositoryPool {
    private var repository: HubRepository? = null
    private var owners = 0

    fun acquire(context: Context): HubRepository {
        val current = repository ?: HubRepository(context).also { repository = it }
        owners++
        return current
    }

    fun release(current: HubRepository) {
        check(repository === current && owners > 0)
        if (--owners == 0) {
            current.close()
            repository = null
        }
    }
}
