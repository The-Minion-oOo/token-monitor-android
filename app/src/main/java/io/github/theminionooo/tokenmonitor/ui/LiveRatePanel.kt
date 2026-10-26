package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LiveRateMode
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.coroutines.delay

@Composable
internal fun LiveRatePanel(snapshot: HubSnapshot, options: DisplayOptions, active: Boolean, source: String?) {
    val tracker = remember(source, active) { LiveThroughputTracker() }
    var reading by remember(tracker) { mutableStateOf<LiveRateReading?>(null) }
    LaunchedEffect(snapshot.capturedAt, tracker) {
        val now = SystemClock.elapsedRealtime()
        reading = if (active && !snapshot.fromCache) tracker.offer(snapshot.today, now) else null
        while (reading != null) {
            delay((reading!!.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            reading = tracker.reading(SystemClock.elapsedRealtime())
        }
    }
    val burn = options.liveRateMode == LiveRateMode.Burn
    val unit = if (burn) "tokens/min" else "tok/s"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(if (burn) appText(R.string.ui_recent_token_burn_056aa, "RECENT TOKEN BURN") else appText(R.string.ui_recent_output_speed_1289e, "RECENT OUTPUT SPEED"), color = Muted, style = MaterialTheme.typography.labelMedium)
        val value = reading?.let { if (burn) it.tokensPerMinute else it.outputPerSecond }
        Text(value?.let { "${formatLiveRate(it)} $unit" } ?: appText(R.string.ui_waiting_for_matched_live_samples_9d2e5, "Waiting for matched live samples"), color = Ink, style = MaterialTheme.typography.bodySmall)
        if (options.showLiveRateModels) reading?.models?.filter { it.model.isNotBlank() }?.sortedByDescending { if (burn) it.tokensPerMinute else it.outputPerSecond }?.take(5)?.forEach { rate ->
            Text("${rate.model.displayName()}: ${formatLiveRate(if (burn) rate.tokensPerMinute else rate.outputPerSecond)} $unit", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun LiveRateSettings(options: DisplayOptions, onChange: (DisplayOptions) -> Unit) {
    SettingsGroup(appText(R.string.ui_live_throughput_ab5cc, "Live throughput"), summary = options.liveRateMode.name) {
        Text(appText(R.string.ui_recent_matched_counter_changes_not_session_average_s_90965, "Recent matched counter changes, not session-average speed. Samples expire after eight seconds. Update frequency stays the same."), color = Muted, style = MaterialTheme.typography.bodySmall)
        TactileSegmentedControl(listOf(appText(R.string.ui_off_ad504, "OFF") to LiveRateMode.Off, appText(R.string.ui_output_s_c2f88, "OUTPUT/S") to LiveRateMode.Speed, appText(R.string.ui_tokens_min_3a4a4, "TOKENS/MIN") to LiveRateMode.Burn), options.liveRateMode) { onChange(options.copy(liveRateMode = it)) }
        SettingsToggle(appText(R.string.ui_show_model_rates_4435d, "Show model rates"), appText(R.string.ui_show_up_to_five_active_reported_models_below_the_tot_9396f, "Show up to five active reported models below the total."), options.showLiveRateModels) { onChange(options.copy(showLiveRateModels = it)) }
    }
}
