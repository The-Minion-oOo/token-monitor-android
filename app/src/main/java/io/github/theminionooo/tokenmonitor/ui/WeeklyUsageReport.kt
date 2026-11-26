package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.shareUsageReport
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.historyInRange
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Limit the shareable card to aggregate readings and labels defined by the app. */
internal fun weeklyUsageReport(history: List<HistoryPoint>, end: LocalDate): TrendPresentation {
    val start = end.minusDays(6)
    val points = historyInRange(history, start, end).map { point ->
        point.copy(perClient = point.perClient.filterKeys { it.knownToolLabel() != null })
    }
    return prepareTrends(points, byModel = false, start = start, end = end)
}

@Composable
internal fun WeeklyUsageReportButton(history: List<HistoryPoint>, cached: Boolean = false, sample: Boolean = false) {
    val today = currentPresentationDate()
    val report = remember(history, today) { weeklyUsageReport(history, today) }
    var preview by remember { mutableStateOf<Pair<TrendPresentation, Boolean>?>(null) }
    OutlinedButton(
        onClick = { preview = report to cached },
        enabled = report.days.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(appText(R.string.usage_report_weekly, "Weekly usage report"))
    }
    preview?.let { (data, wasCached) -> WeeklyUsageReportDialog(data, wasCached, sample) { preview = null } }
}

@Composable
private fun WeeklyUsageReportDialog(report: TrendPresentation, cached: Boolean, sample: Boolean, onDismiss: () -> Unit) {
    val title = appText(R.string.usage_report_weekly, "Weekly usage report")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }
    var shareFailed by remember { mutableStateOf(false) }

    UsageDetailDialog(title, onDismiss, actions = {
        TextButton(
            enabled = !sharing,
            onClick = {
                sharing = true
                shareFailed = false
                scope.launch {
                    try {
                        shareUsageReport(context, layer.toImageBitmap().asAndroidBitmap(), report.end, title)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        shareFailed = true
                    } finally {
                        sharing = false
                    }
                }
            },
        ) { Text(appText(R.string.ui_share_09ca5, "Share")) }
    }) {
        UsageReportCard(report, title, cached, sample, modifier = Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        })
        if (shareFailed) {
            Text(appText(R.string.usage_report_share_failed, "The report could not be shared. Try again."), color = DangerText)
        }
    }
}

@Composable
private fun UsageReportCard(report: TrendPresentation, title: String, cached: Boolean, sample: Boolean, modifier: Modifier) {
    val unpriced = report.days.sumOf { it.unpricedTokens }
    val cost = displayUsageCost(report.days.sumOf { it.costUsd }, unpriced)
    val locale = AppLanguage.locale()
    val percents = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    }
    val palette = LocalPalette.current
    Column(
        modifier.fillMaxWidth().background(Recessed).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Token Monitor", color = Accent, style = MaterialTheme.typography.labelLarge)
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
        Text("${report.start} – ${report.end}", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (sample) Text(appText(R.string.usage_report_sample, "Sample data"), color = OrangeText)
        if (cached) Text(appText(R.string.usage_report_cached, "Based on saved Hub readings."), color = Muted)
        Text(
            appText(R.string.ui_1_s_tokens_2_s_estimated_cost_ce601, "%1\$s tokens, %2\$s estimated cost", formatTokens(report.totalTokens), cost),
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        // A share tapped immediately after opening must capture the complete bars.
        CompositionLocalProvider(LocalInteractionMotion provides false) {
            StackedTrendChart(trend = report, height = 96.dp)
        }
        Text(
            appText(
                R.string.usage_report_coverage,
                "%1\$s of %2\$s days recorded",
                report.days.size,
                report.calendarDays,
            ),
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (report.series.isNotEmpty()) {
            Text(appText(R.string.ui_by_tool_c2917, "BY TOOL"), color = Ink, style = MaterialTheme.typography.labelMedium)
            for ((index, series) in report.series.withIndex()) {
                val label = when {
                    series.other -> appText(R.string.ui_other_tools_e9c83, "Other tools")
                    series.name == null -> appText(R.string.ui_unattributed_61047, "Unattributed")
                    else -> requireNotNull(series.name.knownToolLabel())
                }
                val total = report.days.sumOf { it.segments[series] ?: 0L }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (series.name == null) palette.muted else trendSeriesColor(palette, index), size = 8.dp)
                    Text(label, modifier = Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.bodySmall)
                    Text(percents.format(total.toDouble() / report.totalTokens.coerceAtLeast(1L)), color = Ink, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
