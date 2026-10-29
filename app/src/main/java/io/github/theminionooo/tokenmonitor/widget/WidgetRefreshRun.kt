package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshInterval
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshSettings

/** Lazy facts keep skipped jobs out of pairing storage and cache construction. */
internal fun widgetRefreshAdmission(
    settings: WidgetRefreshSettings,
    widgets: () -> Boolean,
    rejected: () -> Boolean,
    busy: () -> Boolean,
    wifiAvailable: () -> Boolean,
): WidgetRefreshDecision = when {
    settings.interval == WidgetRefreshInterval.Off || !widgets() || rejected() -> WidgetRefreshDecision.Cancel
    busy() || (settings.wifiOnly && !wifiAvailable()) -> WidgetRefreshDecision.Skip
    else -> WidgetRefreshDecision.Run
}

/** Admission cancellations are separate from failures after a bounded run has started. */
internal class WidgetRefreshRun(private val deadline: Long) {
    var completed = false
        private set

    fun beforeRequest(now: Long, wifiAvailable: Boolean) {
        if (now >= deadline) throw WidgetRefreshFailure("Scheduled widget refresh reached its time limit")
        if (!wifiAvailable) throw WidgetRefreshFailure("Wi-Fi unavailable")
    }

    fun applied() { completed = true }
}

internal class WidgetRefreshFailure(message: String) : Exception(message)

/** Keep resource acquisition after admission, including when a previous job was still finishing. */
internal suspend fun <T> runAdmittedWidgetRefresh(
    admit: () -> WidgetRefreshDecision,
    acquire: () -> T,
    release: (T) -> Unit,
    refresh: suspend (T) -> Unit,
) {
    if (admit() != WidgetRefreshDecision.Run) return
    val repository = acquire()
    try { refresh(repository) } finally { release(repository) }
}
