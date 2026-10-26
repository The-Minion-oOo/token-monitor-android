package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.ui.appText
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import io.github.theminionooo.tokenmonitor.ui.formatTokens
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import kotlin.math.roundToInt

/** Describe the visible page, without speaking account identities or session titles. */
internal fun widgetDeckPageSummary(page: WidgetDeckPage, data: WidgetDeckData): String {
    val snapshot = data.snapshot ?: return data.emptyMessage
    fun usage(row: WidgetDeckUsageRow, tool: Boolean): String =
        appText(R.string.ui_1_s_2_s_tokens_3_s_share_4_s_estimated_cost_94df2, "%1\$s: %2\$s tokens, %3\$s%% share, %4\$s estimated cost.", if (tool) row.name.displayName() else row.name, formatTokens(row.tokens), (row.share * 100).roundToInt(), formatUsageCost(row.costUsd, row.unpricedTokens, currency = data.currency))
    return when (page) {
        WidgetDeckPage.Overview -> buildString {
            append(appText(R.string.ui_1_s_tokens_2_s_estimated_cost_for_3_s_65e59, "%1\$s tokens, %2\$s estimated cost for %3\$s. ", formatTokens(snapshot.today.totalTokens), formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens, currency = data.currency), data.date))
            data.stats.take(3).forEach { (value, label) -> append("$label: $value. ") }
            data.tools.take(3).forEach { append(appText(R.string.ui_1_s_2_s_share_3d76b, "%1\$s: %2\$s%% share. ", it.name.displayName(), (it.share * 100).roundToInt())) }
            append(appText(R.string.ui_last_seven_days_1_s_recorded_tokens_2_s_estimated_co_12f4d, "Last seven days: %1\$s recorded tokens, %2\$s estimated cost.", formatTokens(data.week.sumOf { it.tokens }), formatUsageCost(data.week.sumOf { it.costUsd }, data.week.sumOf { it.unpricedTokens }, currency = data.currency)))
        }
        WidgetDeckPage.Limits -> data.limitGroups.take(WidgetDeckGrid.Limits.ROW_TOPS.size).flatMap { group ->
            group.windows.take(2).map { window ->
                appText(R.string.ui_1_s_2_s_3_s_left_4_s_313e9, "%1\$s %2\$s: %3\$s%% left. %4\$s.", group.provider.providerLabel(), window.title, window.remainingPercent.roundToInt(), window.reset.ifBlank { "Reset not reported" })
            }
        }.joinToString(" ").ifBlank { appText(R.string.ui_no_quota_windows_reported_8ba8c, "No quota windows reported.") }
        WidgetDeckPage.Breakdown -> buildString {
            if (data.tools.isEmpty() && data.models.isEmpty()) append(appText(R.string.ui_no_tool_or_model_breakdown_reported_660ff, "No tool or model breakdown reported."))
            else {
                append(appText(R.string.ui_tools_beb93, "Tools. "))
                data.tools.take(WidgetDeckGrid.Breakdown.ROW_TOPS.size).forEach { append(usage(it, true)).append(' ') }
                if (data.tools.isEmpty()) append(appText(R.string.ui_no_tool_breakdown_reported_2d1f7, "No tool breakdown reported. "))
                append(appText(R.string.ui_models_5174c, "Models. "))
                data.models.take(WidgetDeckGrid.Breakdown.ROW_TOPS.size).forEach { append(usage(it, false)).append(' ') }
                if (data.models.isEmpty()) append(appText(R.string.ui_no_model_breakdown_reported_e43f0, "No model breakdown reported."))
            }
        }
        WidgetDeckPage.Activity -> if (data.history.isEmpty()) appText(R.string.ui_no_activity_history_reported_be4f1, "No activity history reported.") else buildString {
            append(appText(R.string.ui_seven_days_ending_1_s_2_s_tokens_3_s_estimated_cost_d6efa, "Seven days ending %1\$s: %2\$s tokens, %3\$s estimated cost. ", data.date, formatTokens(data.week.sumOf { it.tokens }), formatUsageCost(data.week.sumOf { it.costUsd }, data.week.sumOf { it.unpricedTokens }, currency = data.currency)))
            append(appText(R.string.ui_1_s_recorded_days_2_s_days_have_no_observation_1367d, "%1\$s recorded days; %2\$s days have no observation. ", data.week.size, (7 - data.week.size).coerceAtLeast(0)))
            append(appText(R.string.ui_peak_day_1_s_tokens_2_s_active_days_in_the_activity_07710, "Peak day: %1\$s tokens. %2\$s active days in the activity grid. ", formatTokens(data.week.maxOfOrNull { it.tokens } ?: 0), data.activeDays))
            if (data.messagesToday > 0) append(appText(R.string.ui_1_s_messages_for_2_s_172eb, "%1\$s messages for %2\$s.", data.messagesToday, data.date))
            else append(appText(R.string.ui_1_s_estimated_cost_for_2_s_0c214, "%1\$s estimated cost for %2\$s.", formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens, currency = data.currency), data.date))
        }
    }.trim()
}
