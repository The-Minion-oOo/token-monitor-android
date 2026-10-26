package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.LimitSpend

/** New API reports billing periods; MiMo's daily and weekly deltas are locally tracked. */
internal fun limitSpendRows(spend: LimitSpend): List<Pair<String, Double>> {
    val rows = if (spend.locallyTracked) listOf(
        appText(R.string.ui_month_spend_99258, "Month spend") to spend.month, appText(R.string.ui_all_time_spend_8c82f, "All-time spend") to spend.allTime,
        appText(R.string.ui_today_spend_tracked_e08c9, "Today spend (tracked)") to spend.today, appText(R.string.ui_week_spend_tracked_a2094, "Week spend (tracked)") to spend.week,
    ) else listOf(
        appText(R.string.ui_today_spend_e69d0, "Today spend") to spend.today, appText(R.string.ui_month_spend_99258, "Month spend") to spend.month,
        appText(R.string.ui_7_day_spend_2e580, "7-day spend") to spend.week, appText(R.string.ui_all_time_spend_8c82f, "All-time spend") to spend.allTime,
    )
    return rows.mapNotNull { (label, amount) -> amount?.let { label to it } }
}
