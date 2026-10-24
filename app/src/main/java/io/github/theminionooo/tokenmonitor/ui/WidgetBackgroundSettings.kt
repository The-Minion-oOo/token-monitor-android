package io.github.theminionooo.tokenmonitor.ui

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
    Text("Background refresh", color = Ink, style = MaterialTheme.typography.bodyMedium)
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
    SettingsToggle("Wi-Fi only", "Use unmetered Wi-Fi, including when Tailscale is connected. Skip when the route cannot be confirmed.",
        settings.wifiOnly) { onChange(settings.copy(wifiOnly = it)) }
    Text("Runs only with a saved Hub and a placed widget. Android may delay updates while asleep. Live and Refresh take priority; background updates show no notification.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    if (record.secretRejected) Text("Background refresh paused: the Hub rejected the secret. Check and save the connection again.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    if (record.receivedBytes > 0) {
        val runs = if (settings.interval.minutes > 0) 1440 / settings.interval.minutes else 0
        Text("Last attempt: ${record.receivedBytes / 1000} KB of response data" +
            if (!record.completed) " (incomplete)." else if (runs > 0) " · about ${String.format(Locale.US, "%.1f", record.receivedBytes * runs / 1_000_000.0)} MB/day at this interval, plus network overhead." else ".",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
