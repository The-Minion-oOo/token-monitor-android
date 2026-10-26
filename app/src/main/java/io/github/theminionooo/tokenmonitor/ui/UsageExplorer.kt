package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.*
import java.time.LocalDate

@Composable
internal fun UsageComparisonPanel(history: List<HistoryPoint>) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    val today = LocalDate.now()
    val comparison = remember(history, days, today) { compareUsage(history, today, days) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        Text(appText(R.string.ui_period_comparison_66c3d, "PERIOD COMPARISON"), color = Ink, style = MaterialTheme.typography.labelMedium)
        ChoiceGroup(listOf(appText(R.string.ui_7_days_ba6e9, "7 DAYS") to "7", appText(R.string.ui_30_days_f1f09, "30 DAYS") to "30"), days.toString(), { days = it.toInt() })
        Text(appText(R.string.ui_last_1_s_completed_days_vs_preceding_2_s_422be, "Last %1\$s completed days vs preceding %2\$s", days, days), color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(appText(R.string.ui_1_s_vs_2_s_tokens_d4026, "%1\$s vs %2\$s tokens", formatCompactTokens(comparison.current.sumOf { it.tokens }), formatCompactTokens(comparison.previous.sumOf { it.tokens })), color = Ink)
        if (comparison.complete) {
            Text(appText(R.string.ui_1_s_tokens_2_s_fdd7d, "%1\$s tokens%2\$s", signedTokens(comparison.tokenDelta), comparison.percentChange?.let { " · %+.1f%%".format(java.util.Locale.US, it) }.orEmpty()), color = Accent)
            Text(appText(R.string.ui_estimated_cost_1_s_vs_2_s_6ae1a, "Estimated cost: %1\$s vs %2\$s", displayUsageCost(comparison.current.sumOf { it.costUsd }, comparison.current.sumOf { it.unpricedTokens }), displayUsageCost(comparison.previous.sumOf { it.costUsd }, comparison.previous.sumOf { it.unpricedTokens })), color = Muted, style = MaterialTheme.typography.bodySmall)
            comparison.drivers(byModel = true).filter { it.second != 0L }.take(3).forEach { (name, delta) ->
                Text(appText(R.string.ui_1_s_2_s_tokens_e2cfb, "%1\$s: %2\$s tokens", name, signedTokens(delta)), color = Ink, style = MaterialTheme.typography.bodySmall)
            }
            Text(appText(R.string.ui_model_changes_use_reported_attribution_missing_break_166cf, "Model changes use reported attribution; missing breakdowns are not estimated."), color = Muted, style = MaterialTheme.typography.labelSmall)
        } else {
            Text(appText(R.string.ui_incomplete_history_1_s_2_s_and_3_s_4_s_days_recorded_e9ac0, "Incomplete history: %1\$s/%2\$s and %3\$s/%4\$s days recorded. Missing days are not treated as zero; percentage change is unavailable.", comparison.current.size, days, comparison.previous.size, days), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = Line)
    }
}

private fun signedTokens(value: Long) = (if (value >= 0) "+" else "−") + formatCompactTokens(kotlin.math.abs(value))

@Composable
internal fun UsageExplorer(history: List<HistoryPoint>) {
    val focus = LocalFocusManager.current
    var models by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val values = remember(history, models, query) {
        history.flatMap { (if (models) it.perModel else it.perClient).entries }
            .groupBy { it.key }.mapValues { (_, entries) -> entries.sumOf { it.value.tokens } }
            .filterKeys { it.contains(query, ignoreCase = true) }.entries.sortedByDescending { it.value }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(appText(R.string.ui_explore_usage_60fda, "EXPLORE USAGE"), color = Ink, style = MaterialTheme.typography.labelMedium)
        Text(appText(R.string.ui_tap_a_tool_or_model_to_see_its_recorded_daily_usage_f6ec4, "Tap a tool or model to see its recorded daily usage."), color = Muted, style = MaterialTheme.typography.bodySmall)
        ChoiceGroup(listOf(appText(R.string.ui_by_model_5d75a, "BY MODEL") to "model", appText(R.string.ui_by_tool_c2917, "BY TOOL") to "tool"), if (models) "model" else "tool", { models = it == "model" })
        CompactSearchField(query, { query = it }, appText(R.string.ui_find_a_tool_or_model_c5588, "Find a tool or model"))
        Column { values.take(8).forEach { (name, value) ->
            ExplorerRow(name, formatCompactTokens(value)) { focus.clearFocus(); selected = name }
        } }
        if (values.size > 8) Text(appText(R.string.ui_1_s_more_matches_refine_the_search_to_find_them_8c54a, "%1\$s more matches. Refine the search to find them.", values.size - 8), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (values.isEmpty()) Text(appText(R.string.ui_no_matching_attribution_in_the_available_history_b8e86, "No matching attribution in the available history."), color = Muted)
    }
    selected?.let { SeriesUsageDialog(it, models, history) { selected = null } }
}

@Composable
internal fun DayUsageDialog(date: String, history: List<HistoryPoint>, onDismiss: () -> Unit) {
    val point = history.firstOrNull { it.label.take(10) == date }
    var models by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if (selected == null) UsageDetailDialog(date, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (point == null) appText(R.string.ui_no_observation_was_supplied_for_this_date_5353b, "No observation was supplied for this date.") else appText(R.string.ui_1_s_tokens_2_s_estimated_cfa9b, "%1\$s tokens · %2\$s estimated", formatTokens(point.tokens), displayUsageCost(point.costUsd, point.unpricedTokens)), color = Ink)
            ChoiceGroup(listOf(appText(R.string.ui_models_441ba, "MODELS") to "model", appText(R.string.ui_tools_9d0e5, "TOOLS") to "tool"), if (models) "model" else "tool", { models = it == "model" })
            val entries = (if (models) point?.perModel else point?.perClient).orEmpty().entries.sortedByDescending { it.value.tokens }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(entries, key = { it.key }) { (name, value) -> ExplorerRow(name, formatCompactTokens(value.tokens)) { selected = name } }
                if (entries.isEmpty()) item { Text(appText(R.string.ui_the_hub_has_no_breakdown_for_this_day_c0785, "The Hub has no breakdown for this day."), color = Muted) }
            }
            Text(appText(R.string.ui_sessions_and_projects_are_available_for_the_hub_s_da_6c231, "Sessions and projects are available for the Hub's Day, Month, and Total periods."), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
    selected?.let { SeriesUsageDialog(it, models, history) { selected = null } }
}

@Composable
private fun SeriesUsageDialog(name: String, models: Boolean, history: List<HistoryPoint>, onDismiss: () -> Unit) {
    val points = history.mapNotNull { point -> (if (models) point.perModel else point.perClient)[name]?.let { point.label to it } }
    UsageDetailDialog(name, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appText(R.string.ui_1_s_tokens_across_2_s_recorded_3_s_381b2, "%1\$s tokens across %2\$s recorded %3\$s", formatCompactTokens(points.sumOf { it.second.tokens }), points.size, if (points.size == 1) "day" else "days"), color = Ink)
            Text(appText(R.string.ui_daily_usage_newest_first_costs_are_estimates_from_th_c37c9, "Daily usage, newest first. Costs are estimates from the desktop Hub."), color = Muted, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(points.reversed(), key = { it.first }) { (date, value) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(date, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(appText(R.string.ui_1_s_tokens_2_s_8aebf, "%1\$s tokens · %2\$s", formatCompactTokens(value.tokens), displayUsageCost(value.costUsd, value.unpricedTokens)), color = Muted)
                        UsageBar(value.tokens.toFloat() / points.maxOf { it.second.tokens }.coerceAtLeast(1), Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerRow(name: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = appText(R.string.ui_explore_1_s_cbf23, "Explore %1\$s", name), onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, color = Ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Accent, style = MaterialTheme.typography.bodySmall)
    }
}

/** Tool-specific attribution must never borrow a model's global totals or breakdown. */
internal fun UsagePeriod.modelsForTool(tool: String): UsagePeriod = UsagePeriod(
    totalTokens = clients[tool] ?: 0,
    costUsd = clientCosts[tool] ?: 0.0,
    unpricedTokens = clientUnpricedTokens[tool] ?: 0,
    modelUnpricedTokens = clientModelUnpricedTokens[tool].orEmpty(),
    models = clientModels[tool].orEmpty(),
    modelCosts = clientModelCosts[tool].orEmpty(),
)

@Composable
internal fun CompactSearchField(value: String, onValueChange: (String) -> Unit, hint: String) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = Ink)
    val border = if (focused) Accent else Line
    Surface(color = Overlay, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, border)) {
        BasicTextField(value, onValueChange, singleLine = true, textStyle = textStyle, cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = hint },
            decorationBox = { inner ->
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(hint, color = Muted, style = MaterialTheme.typography.bodySmall)
                        inner()
                    }
                    if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = appText(R.string.ui_clear_search_67300, "Clear search"), tint = Muted, modifier = Modifier.size(18.dp))
                    } else Spacer(Modifier.width(12.dp))
                }
            })
    }
}

@Composable
private fun UsageDetailDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { containerHeight.toDp() * 0.85f }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Shell, contentColor = Ink, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, StrongLine)) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = Line)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                HorizontalDivider(color = Line)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(appText(R.string.ui_close_dbc87, "CLOSE"), color = Accent, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
