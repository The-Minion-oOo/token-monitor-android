package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.HistoryAttribution
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import java.time.LocalDate

internal fun sampleUsageHistory(today: LocalDate): List<HistoryPoint> {
    val dailyTokens = listOf(1_600_000L, 2_100_000L, 1_400_000L, 3_200_000L, 2_700_000L, 2_800_000L, 4_600_000L)
    return dailyTokens.mapIndexed { day, tokens ->
        val claude = tokens / 2
        val codex = tokens * 3 / 10
        HistoryPoint(
            label = today.minusDays((6 - day).toLong()).toString(),
            tokens = tokens,
            costUsd = tokens / 2_000_000.0,
            perClient = mapOf(
                "claude" to HistoryAttribution(claude),
                "codex" to HistoryAttribution(codex),
                "opencode" to HistoryAttribution(tokens - claude - codex),
            ),
        )
    }
}

@Composable
internal fun SampleUsageButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(appText(R.string.usage_report_try_sample, "Try sample data"))
    }
    if (open) {
        val today = currentPresentationDate()
        val history = remember(today) { sampleUsageHistory(today) }
        UsageDetailDialog(appText(R.string.usage_report_sample, "Sample data"), onDismiss = { open = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WeeklyUsageReportButton(history, sample = true)
                UsageOverview(history)
            }
        }
    }
}
