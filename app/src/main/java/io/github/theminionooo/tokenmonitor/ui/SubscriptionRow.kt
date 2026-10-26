package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.Subscription
import java.time.LocalDate

@Composable
internal fun SubscriptionRow(subscription: Subscription, showEmail: Boolean = false) {
    var expanded by rememberSaveable(subscription.id) { mutableStateOf(false) }
    var shownPayments by rememberSaveable(subscription.id) { mutableIntStateOf(10) }
    val topUp = subscription.isTopUp()
    val amount = if (topUp) topUpTotalMinor(subscription) else subscription.amountMinor.takeIf { subscription.amountReported && subscription.kind == "subscription" }
    val boundary = subscriptionBoundary(subscription, LocalDate.now())
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.clickable(onClickLabel = "Show payment details") { expanded = !expanded }) {
            DesktopDetailRow(
                label = subscription.provider.providerLabel(),
                subtitle = subscription.planName.ifBlank { if (topUp) "Top-ups" else "Subscription" },
                value = amount?.let { formatSubscriptionAmount(it, subscription.currency) } ?: "Not reported",
                detail = if (topUp) "Total paid" else subscriptionCadence(subscription),
                color = Purple, upstreamName = subscription.provider,
            )
        }
        if (boundary != null) Text("${if (subscription.autoRenew && subscription.endDate.isBlank()) "Next renewal" else "Coverage ends"}: $boundary", color = Muted, style = MaterialTheme.typography.labelSmall)
        if (!subscription.autoRenew && !topUp) Text("Auto-renew off", color = Muted, style = MaterialTheme.typography.labelSmall)
        val identity = listOf(subscription.binding.profileName, subscription.binding.accountEmail.takeIf { showEmail }.orEmpty()).filter(String::isNotBlank).joinToString(" · ")
        if (identity.isNotBlank()) Text(identity, color = Muted, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide payment details" else "Show payment details") }
        if (expanded) {
            if (subscription.startDate.isNotBlank() && !topUp) Text("Started: ${subscription.startDate}", color = Muted, style = MaterialTheme.typography.labelSmall)
            if (subscription.note.isNotBlank()) Text(subscription.note, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (topUp && subscription.topUps.isEmpty()) Text("No recorded top-ups", color = Muted, style = MaterialTheme.typography.labelSmall)
            subscription.topUps.take(shownPayments).forEach { payment ->
                Text("${payment.date} · ${formatSubscriptionAmount(payment.amountMinor, subscription.currency)}", color = Ink, style = MaterialTheme.typography.bodySmall)
            }
            if (subscription.topUps.size > shownPayments) TextButton(onClick = { shownPayments += 10 }) { Text("Show more payments") }
        }
    }
}
