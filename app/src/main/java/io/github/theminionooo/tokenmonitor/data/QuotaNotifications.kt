package io.github.theminionooo.tokenmonitor.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.HubLimits
import io.github.theminionooo.tokenmonitor.domain.QuotaAlert
import io.github.theminionooo.tokenmonitor.domain.QuotaAlertDetector
import io.github.theminionooo.tokenmonitor.domain.QuotaReading
import io.github.theminionooo.tokenmonitor.ui.appText
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

internal class QuotaNotifications(private val context: Context) {
    private val preferences = context.getSharedPreferences("quota_alerts", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var detector = QuotaAlertDetector()
    private var baselineScope: Pair<String, Int>? = null

    fun isEnabled(alert: QuotaAlert): Boolean = preferences.getBoolean(alert.preference, false)

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setEnabled(alert: QuotaAlert, enabled: Boolean) {
        if (isEnabled(alert) == enabled) return
        val baselines = preferences.all.keys.filter { it.startsWith(BASELINE_PREFIX) }
        preferences.edit {
            putBoolean(alert.preference, enabled)
            putInt("settings_revision", preferences.getInt("settings_revision", 0) + 1)
            baselines.forEach { remove(it) }
        }
        if (!enabled) manager.cancel(alert.notificationId)
    }

    fun resetForHubChange() {
        baselineScope = null
        detector.clear()
        QuotaAlert.entries.forEach { manager.cancel(it.notificationId) }
    }

    fun forgetProfile(id: String) {
        preferences.edit { remove(BASELINE_PREFIX + id) }
    }

    fun observe(profileId: String, limits: HubLimits) {
        val enabled = QuotaAlert.entries.filterTo(mutableSetOf(), ::isEnabled)
        if (enabled.isEmpty()) return
        val key = BASELINE_PREFIX + profileId
        if (!hasPermission() || !manager.areNotificationsEnabled()) {
            baselineScope = null
            if (preferences.contains(key)) preferences.edit { remove(key) }
            return
        }
        val scope = profileId to preferences.getInt("settings_revision", 0)
        if (baselineScope != scope) {
            baselineScope = scope
            detector = QuotaAlertDetector(readBaseline(preferences.getString(key, null)))
        }
        val previous = detector.readings
        val changes = detector.observe(limits.providers, Instant.now()).intersect(enabled)
        if (previous != detector.readings) {
            preferences.edit { putString(key, encodeBaseline(detector.readings)) }
        }
        if (changes.isEmpty()) return
        val title = appText(R.string.quota_alert_title, "Quota alerts")
        manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 23,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        for (alert in changes) {
            val message = when (alert) {
                QuotaAlert.Low -> appText(R.string.quota_alert_low_notice, "A usage quota is nearly full.")
                QuotaAlert.Reset -> appText(R.string.quota_alert_reset_notice, "A usage quota has reset.")
            }
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_widget_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // Permission can be revoked after the check above.
            try { manager.notify(alert.notificationId, notification) } catch (_: SecurityException) { }
        }
    }

    private fun readBaseline(raw: String?): Map<String, QuotaReading> {
        val stored = raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return emptyMap()
        return stored.mapNotNull { (key, element) ->
            val value = element as? JsonObject ?: return@mapNotNull null
            val at = (value["at"] as? JsonPrimitive)?.contentOrNull?.let(::instant) ?: return@mapNotNull null
            val used = (value["used"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.0..100.0 }
                ?: return@mapNotNull null
            val low = (value["low"] as? JsonPrimitive)?.booleanOrNull ?: return@mapNotNull null
            val reset = (value["reset"] as? JsonPrimitive)?.contentOrNull?.let(::instant)
            key to QuotaReading(at, used, reset, low)
        }.toMap()
    }

    private fun instant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    private fun encodeBaseline(readings: Map<String, QuotaReading>): String = buildJsonObject {
        for ((key, reading) in readings) put(key, buildJsonObject {
            put("at", reading.at.toString())
            put("used", reading.used)
            put("reset", reading.resetsAt?.toString())
            put("low", reading.low)
        })
    }.toString()

    private val QuotaAlert.preference get() = when (this) {
        QuotaAlert.Low -> "low"
        QuotaAlert.Reset -> "reset"
    }

    private val QuotaAlert.notificationId get() = when (this) {
        QuotaAlert.Low -> 72
        QuotaAlert.Reset -> 73
    }

    private companion object {
        const val CHANNEL = "quota_alerts"
        const val BASELINE_PREFIX = "baseline_"
    }
}
