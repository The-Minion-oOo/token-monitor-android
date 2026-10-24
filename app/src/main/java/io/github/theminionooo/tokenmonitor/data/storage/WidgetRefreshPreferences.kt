package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import androidx.core.content.edit

internal enum class WidgetRefreshInterval(val minutes: Int, val label: String) {
    Off(0, "Off"), Thirty(30, "Every 30 minutes"), Sixty(60, "Every 60 minutes");
    companion object {
        fun fromMinutes(minutes: Int) = entries.firstOrNull { it.minutes == minutes } ?: Off
    }
}

internal data class WidgetRefreshSettings(
    val interval: WidgetRefreshInterval = WidgetRefreshInterval.Off,
    val wifiOnly: Boolean = true,
)

internal data class WidgetRefreshRecord(
    val successAt: Long = 0,
    val failureAt: Long = 0,
    val secretRejected: Boolean = false,
    val receivedBytes: Long = 0,
    val completed: Boolean = false,
) {
    fun stale(capturedAt: Long): Boolean = failureAt > 0 && failureAt >= capturedAt && failureAt > successAt
}

/** Non-secret consent and run metadata; no URLs, credentials or response bodies. */
internal class WidgetRefreshPreferences(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences("widget_refresh", Context.MODE_PRIVATE)
    val settings get() = WidgetRefreshSettings(
        WidgetRefreshInterval.fromMinutes(preferences.getInt("minutes", 0)),
        preferences.getBoolean("wifi_only", true),
    )
    val record get() = WidgetRefreshRecord(
        preferences.getLong("success_at", 0), preferences.getLong("failure_at", 0),
        preferences.getBoolean("rejected", false), preferences.getLong("received_bytes", 0), preferences.getBoolean("completed", false),
    )
    fun save(settings: WidgetRefreshSettings) = preferences.edit {
        putInt("minutes", settings.interval.minutes); putBoolean("wifi_only", settings.wifiOnly)
    }
    fun success(at: Long) = preferences.edit {
        putLong("success_at", maxOf(at, record.successAt))
        // A queued older cache write cannot forgive a later authentication failure.
        if (at >= record.failureAt) putBoolean("rejected", false)
    }
    fun failure(at: Long, rejected: Boolean) = preferences.edit(commit = true) {
        putLong("failure_at", at)
        if (rejected) putBoolean("rejected", true)
    }
    fun traffic(bytes: Long, completed: Boolean) = preferences.edit {
        putLong("received_bytes", bytes); putBoolean("completed", completed)
    }
    fun clearRecord() = preferences.edit(commit = true) {
        remove("success_at"); remove("failure_at"); remove("rejected"); remove("received_bytes"); remove("completed")
    }
}
