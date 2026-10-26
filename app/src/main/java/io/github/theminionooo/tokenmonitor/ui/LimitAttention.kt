package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot

@Composable
internal fun LimitAttentionPanel(snapshot: HubSnapshot) {
    val notes = limitAttention(snapshot, LocalNow.current)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(appText(R.string.ui_quota_at_a_glance_d7262, "QUOTA AT A GLANCE"), color = Ink, style = MaterialTheme.typography.labelMedium)
        notes.take(4).forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        if (notes.size > 4) Text(appText(R.string.ui_1_s_more_quota_notices_see_the_accounts_below_34cc3, "%1\$s more quota notices; see the accounts below.", notes.size - 4), color = Muted, style = MaterialTheme.typography.labelSmall)
        if (notes.isEmpty()) Text(if (snapshot.stats.limits.providers.none { it.windows.isNotEmpty() }) appText(R.string.ui_no_quota_windows_were_reported_53886, "No quota windows were reported.") else appText(R.string.ui_no_low_quota_or_imminent_reset_in_the_reported_windo_41196, "No low quota or imminent reset in the reported windows."), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(appText(R.string.ui_quota_is_provider_reported_usage_costs_are_estimates_6c8ad, "Quota is provider-reported. Usage costs are estimates; subscriptions are recorded payments."), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
