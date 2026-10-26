package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.LimitAccount

@Composable
internal fun ProviderDetailRows(account: LimitAccount) {
    var expanded by rememberSaveable(limitAccountIdentity(account)) { mutableStateOf(false) }
    val credits = account.resetCredits
    val usage = account.usageSummary
    if (credits == null && usage == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (credits != null) {
            Text(appText(R.string.ui_reset_credits_1_s_c22df, "Reset credits: %1\$s", credits.availableCount?.toString() ?: "Not reported"), color = Ink, style = MaterialTheme.typography.bodySmall)
            val boundary = formatBoundary(credits.nextExpiresAt.ifBlank { credits.expirations.firstOrNull().orEmpty() }, "expiry", LocalNow.current)
            if (boundary.isNotBlank()) Text(boundary, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        usage?.todayTokens?.let { Text(appText(R.string.ui_today_1_s_tokens_cb898, "Today: %1\$s tokens", formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
        usage?.totalTokens?.let { Text(appText(R.string.ui_1_s_2_s_tokens_e2cfb, "%1\$s: %2\$s tokens", usage.period.displayName(), formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) appText(R.string.ui_hide_quota_details_70b94, "Hide quota details") else appText(R.string.ui_show_quota_details_14ec2, "Show quota details")) }
        if (expanded) {
            credits?.expirations.orEmpty().forEach { expiry ->
                formatBoundary(expiry, appText(R.string.ui_expiry_7ea8c, "expiry"), LocalNow.current).takeIf(String::isNotBlank)?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            credits?.grants.orEmpty().forEach { grant ->
                Text(grant.label.ifBlank { appText(R.string.ui_reset_grant_16ed0, "Reset grant") }, color = Ink, style = MaterialTheme.typography.bodySmall)
                grant.resetsLeft?.let { Text(appText(R.string.ui_remaining_resets_1_s_802ca, "Remaining resets: %1\$s", it), color = Muted, style = MaterialTheme.typography.labelSmall) }
                grant.resetsTotal?.let { Text(appText(R.string.ui_granted_resets_1_s_f5b55, "Granted resets: %1\$s", it), color = Muted, style = MaterialTheme.typography.labelSmall) }
                grant.usableNow?.let { Text(if (it) appText(R.string.ui_usable_now_2615c, "Usable now") else appText(R.string.ui_not_usable_now_3c249, "Not usable now"), color = Muted, style = MaterialTheme.typography.labelSmall) }
                if (grant.useRequiresLimit == true) Text(appText(R.string.ui_requires_a_reached_limit_0fea2, "Requires a reached limit"), color = Muted, style = MaterialTheme.typography.labelSmall)
                if (grant.clears.isNotEmpty()) Text(appText(R.string.ui_clears_1_s_38e14, "Clears: %1\$s", grant.clears.joinToString(", ")), color = Muted, style = MaterialTheme.typography.labelSmall)
                if (grant.startsAt.isNotBlank()) Text(appText(R.string.ui_starts_1_s_9fb18, "Starts: %1\$s", grant.startsAt), color = Muted, style = MaterialTheme.typography.labelSmall)
                formatBoundary(grant.endsAt, appText(R.string.ui_expiry_7ea8c, "expiry"), LocalNow.current).takeIf(String::isNotBlank)?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            usage?.weekTokens?.let { Text(appText(R.string.ui_last_7_days_1_s_tokens_38e26, "Last 7 days: %1\$s tokens", formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.inputTokens?.let { Text(appText(R.string.ui_input_1_s_tokens_c42a8, "Input: %1\$s tokens", formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.outputTokens?.let { Text(appText(R.string.ui_output_1_s_tokens_162e2, "Output: %1\$s tokens", formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.requests?.let { Text(appText(R.string.ui_requests_1_s_17086, "Requests: %1\$s", formatTokens(it)), color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.standardCost?.let { Text(appText(R.string.ui_reported_estimated_spend_1_s_67ab8, "Reported estimated spend: %1\$s", formatWindowAmount(it, "USD")), color = Muted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
