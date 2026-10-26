package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshSettings
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshRecord
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshInterval
import io.github.theminionooo.tokenmonitor.widget.WidgetRefreshScheduler
import java.util.Locale

@Composable
internal fun WidgetBackgroundSettings() {
    val context = LocalContext.current
    val store = remember(context) { WidgetRefreshPreferences(context) }
    var settings by remember { mutableStateOf(store.settings) }
    var record by remember { mutableStateOf(store.record) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = store.settings
            record = store.record
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    WidgetBackgroundControls(settings, record) {
        settings = it
        store.save(it)
        WidgetRefreshScheduler.reconcileAsync(context)
    }
}

@Composable
internal fun WidgetBackgroundControls(
    settings: WidgetRefreshSettings,
    record: WidgetRefreshRecord,
    onChange: (WidgetRefreshSettings) -> Unit,
) {
    Text(appText(R.string.ui_background_refresh_332cc, "Background refresh"), color = Ink, style = MaterialTheme.typography.bodyMedium)
    // Separate rows keep the full interval labels readable on small screens and with larger text.
    WidgetRefreshInterval.entries.forEach { interval ->
        Row(Modifier.fillMaxWidth().selectable(settings.interval == interval,
            role = Role.RadioButton,
            onClick = { onChange(settings.copy(interval = interval)) }), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = settings.interval == interval, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Muted))
            Text(interval.label, color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
    SettingsToggle(appText(R.string.ui_wi_fi_only_119cb, "Wi-Fi only"), appText(R.string.ui_use_unmetered_wi_fi_including_when_tailscale_is_conn_34b2f, "Use unmetered Wi-Fi, including when Tailscale is connected. Skip when the route cannot be confirmed."),
        settings.wifiOnly) { onChange(settings.copy(wifiOnly = it)) }
    Text(appText(R.string.ui_runs_only_with_a_saved_hub_and_a_placed_widget_andro_e4bc8, "Runs only with a saved Hub and a placed widget. Android may delay updates while asleep. Live and Refresh take priority; background updates show no notification."),
        color = Muted, style = MaterialTheme.typography.bodySmall)
    if (record.secretRejected) Text(appText(R.string.ui_background_refresh_paused_the_hub_rejected_the_secre_09c59, "Background refresh paused: the Hub rejected the secret. Check and save the connection again."),
        color = Muted, style = MaterialTheme.typography.bodySmall)
    if (record.receivedBytes > 0) {
        val runs = if (settings.interval.minutes > 0) 1440 / settings.interval.minutes else 0
        Text(appText(R.string.ui_last_attempt_1_s_kb_of_response_data_65c84, "Last attempt: %1\$s KB of response data", record.receivedBytes / 1000) +
            if (!record.completed) appText(R.string.ui_incomplete_69b4a, " (incomplete).") else if (runs > 0) appText(R.string.ui_about_1_s_mb_day_at_this_interval_plus_network_overh_99a60, " · about %1\$s MB/day at this interval, plus network overhead.", String.format(Locale.US, "%.1f", record.receivedBytes * runs / 1_000_000.0)) else ".",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
