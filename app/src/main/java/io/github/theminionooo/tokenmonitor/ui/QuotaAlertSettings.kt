package io.github.theminionooo.tokenmonitor.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.QuotaNotifications
import io.github.theminionooo.tokenmonitor.domain.QUOTA_ALERT_THRESHOLD
import io.github.theminionooo.tokenmonitor.domain.QuotaAlert

@Composable
internal fun QuotaAlertSettings() {
    val context = LocalContext.current
    val notifications = remember(context) { QuotaNotifications(context) }
    var enabled by remember(notifications) { mutableStateOf(QuotaAlert.entries.filter(notifications::isEnabled).toSet()) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    fun update(alert: QuotaAlert, on: Boolean) {
        notifications.setEnabled(alert, on)
        enabled = if (on) enabled + alert else enabled - alert
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val alert = QuotaAlert.entries.firstOrNull { it.name == pending }
        if (granted && alert != null) update(alert, true)
        pending = null
        permissionDenied = !granted
    }
    fun toggle(alert: QuotaAlert, on: Boolean) {
        if (pending != null) return
        permissionDenied = false
        if (!on || notifications.hasPermission()) update(alert, on)
        else {
            pending = alert.name
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    SettingsGroup(appText(R.string.quota_alert_title, "Quota alerts")) {
        Text(appText(R.string.quota_alert_description, "Alerts use fresh readings from the active Hub while the app or widget is updating."),
            style = MaterialTheme.typography.bodySmall, color = Muted)
        SettingsToggle(
            appText(R.string.quota_alert_low, "Low quota"),
            appText(R.string.quota_alert_low_description, "Alert when a usage window reaches %1\$d%% used.", QUOTA_ALERT_THRESHOLD),
            QuotaAlert.Low in enabled,
        ) { toggle(QuotaAlert.Low, it) }
        SettingsToggle(
            appText(R.string.quota_alert_reset, "Quota resets"),
            appText(R.string.quota_alert_reset_description, "Alert when the Hub reports lower usage and a new reset time."),
            QuotaAlert.Reset in enabled,
        ) { toggle(QuotaAlert.Reset, it) }
        if (permissionDenied) {
            Text(appText(R.string.quota_alert_permission, "Allow notifications in Android settings to receive quota alerts."),
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
