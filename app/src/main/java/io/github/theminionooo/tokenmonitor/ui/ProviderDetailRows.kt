package io.github.theminionooo.tokenmonitor.ui

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
            Text("Reset credits: ${credits.availableCount?.toString() ?: "Not reported"}", color = Ink, style = MaterialTheme.typography.bodySmall)
            val boundary = formatBoundary(credits.nextExpiresAt.ifBlank { credits.expirations.firstOrNull().orEmpty() }, "expiry", LocalNow.current)
            if (boundary.isNotBlank()) Text(boundary, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        usage?.todayTokens?.let { Text("Today: ${formatTokens(it)} tokens", color = Muted, style = MaterialTheme.typography.labelSmall) }
        usage?.totalTokens?.let { Text("${usage.period.displayName()}: ${formatTokens(it)} tokens", color = Muted, style = MaterialTheme.typography.labelSmall) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide quota details" else "Show quota details") }
        if (expanded) {
            credits?.expirations.orEmpty().forEach { expiry ->
                formatBoundary(expiry, "expiry", LocalNow.current).takeIf(String::isNotBlank)?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            credits?.grants.orEmpty().forEach { grant ->
                Text(grant.label.ifBlank { "Reset grant" }, color = Ink, style = MaterialTheme.typography.bodySmall)
                grant.resetsLeft?.let { Text("Remaining resets: $it", color = Muted, style = MaterialTheme.typography.labelSmall) }
                grant.resetsTotal?.let { Text("Granted resets: $it", color = Muted, style = MaterialTheme.typography.labelSmall) }
                grant.usableNow?.let { Text(if (it) "Usable now" else "Not usable now", color = Muted, style = MaterialTheme.typography.labelSmall) }
                if (grant.useRequiresLimit == true) Text("Requires a reached limit", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (grant.clears.isNotEmpty()) Text("Clears: ${grant.clears.joinToString(", ")}", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (grant.startsAt.isNotBlank()) Text("Starts: ${grant.startsAt}", color = Muted, style = MaterialTheme.typography.labelSmall)
                formatBoundary(grant.endsAt, "expiry", LocalNow.current).takeIf(String::isNotBlank)?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            usage?.weekTokens?.let { Text("Last 7 days: ${formatTokens(it)} tokens", color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.inputTokens?.let { Text("Input: ${formatTokens(it)} tokens", color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.outputTokens?.let { Text("Output: ${formatTokens(it)} tokens", color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.requests?.let { Text("Requests: ${formatTokens(it)}", color = Muted, style = MaterialTheme.typography.labelSmall) }
            usage?.standardCost?.let { Text("Reported estimated spend: ${formatWindowAmount(it, "USD")}", color = Muted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
