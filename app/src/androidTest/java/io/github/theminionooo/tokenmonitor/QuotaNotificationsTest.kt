package io.github.theminionooo.tokenmonitor

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.QuotaNotifications
import io.github.theminionooo.tokenmonitor.domain.HubLimits
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.LimitWindow
import io.github.theminionooo.tokenmonitor.domain.QuotaAlert
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class QuotaNotificationsTest {
    @Test fun androidReceivesAnOptInQuotaAlertAndClearsItOnHubSwitch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        check(target.packageName.endsWith(".preview"))
        val prefix = "quota_${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("${prefix}_$name", mode)
        }
        val manager = target.getSystemService(NotificationManager::class.java)
        val notifications = QuotaNotifications(context)
        val start = Instant.now().minusSeconds(30)
        fun limits(used: Double, offset: Long): HubLimits = HubLimits(providers = listOf(
            LimitAccount("codex", "Synthetic account", "example@example.com", "", "ok", "desktop",
                start.plusSeconds(offset).toString(), listOf(LimitWindow("session", "Session", used,
                    null, null, "", "", "", "", true))),
        ))
        fun alerts() = manager.activeNotifications.filter { it.notification.channelId == "quota_alerts" }
        fun awaitAlerts(expected: Int) {
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while (alerts().size != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertEquals(expected, alerts().size)
        }
        if (Build.VERSION.SDK_INT >= 33 && !notifications.hasPermission()) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            assertFalse(notifications.isEnabled(QuotaAlert.Low))
            notifications.observe("first", limits(40.0, 0))
            notifications.observe("first", limits(90.0, 1))
            assertTrue(alerts().isEmpty())
            notifications.setEnabled(QuotaAlert.Low, true)
            notifications.observe("first", limits(40.0, 2))
            // A new repository can be created for each periodic widget run.
            QuotaNotifications(context).observe("second", limits(90.0, 3))
            awaitAlerts(0)
            QuotaNotifications(context).observe("first", limits(90.0, 3))
            awaitAlerts(1)
            val alert = alerts().single().notification
            assertEquals(Notification.VISIBILITY_PRIVATE, alert.visibility)
            assertFalse(alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("example@example.com"))
            notifications.resetForHubChange()
            notifications.observe("second", limits(99.0, 4))
            awaitAlerts(0)
        } finally {
            notifications.setEnabled(QuotaAlert.Low, false)
            notifications.resetForHubChange()
        }
    }
}
