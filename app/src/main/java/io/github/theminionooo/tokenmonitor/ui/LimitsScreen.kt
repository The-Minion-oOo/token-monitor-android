package io.github.theminionooo.tokenmonitor.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import io.github.theminionooo.tokenmonitor.domain.ServiceProviderStatus
import io.github.theminionooo.tokenmonitor.domain.Subscription
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * Limits and Subscriptions lists.
 */
internal fun LazyListScope.limitItems(snapshot: HubSnapshot, displayOptions: DisplayOptions) {
    val visibleAccounts = io.github.theminionooo.tokenmonitor.data.storage.visibleLimitAccounts(snapshot.stats.limits.providers, displayOptions)
    item { LimitAttentionPanel(snapshot.copy(stats = snapshot.stats.copy(limits = snapshot.stats.limits.copy(providers = visibleAccounts)))) }
    if (snapshot.stats.limits.providers.isEmpty()) item { MutedCopy(appText(R.string.ui_no_account_limits_are_available_from_this_hub_yet_ed63e, "No account limits are available from this Hub yet."), modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val providers = prioritizeAvailableLimits(visibleAccounts)
        val keys = uniqueKeys(providers, ::limitAccountIdentity)
        itemsIndexed(providers, key = { index, _ -> keys[index] }) { _, account ->
            LimitAccountRow(account, displayOptions)
        }
    }
    if (snapshot.subscriptions.entries.isNotEmpty()) {
        item { Text(appText(R.string.ui_subscriptions_5c8f4, "SUBSCRIPTIONS"), color = Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        val subscriptions = snapshot.subscriptions.entries
        val keys = uniqueKeys(subscriptions) { it.id }
        itemsIndexed(subscriptions, key = { index, _ -> keys[index] }) { _, subscription -> SubscriptionRow(subscription, displayOptions.showAccountEmails) }
    }
}

/** The desktop's wording for a provider row that has no quota window to draw. */
internal fun limitStatusLabel(status: String): String = when (status) {
    "notConfigured" -> appText(R.string.ui_not_signed_in_848d8, "Not signed in")
    "unauthorized" -> appText(R.string.ui_sign_in_again_7842f, "Sign in again")
    "disabled" -> appText(R.string.ui_disabled_f4f44, "Disabled")
    "noSyncedData" -> appText(R.string.ui_no_synced_data_10b38, "No synced data")
    "rateLimited", "sourceRateLimited" -> appText(R.string.ui_limited_ceeaa, "Limited")
    "unavailable", "error" -> appText(R.string.ui_unavailable_2c9c1, "Unavailable")
    "ok" -> appText(R.string.ui_no_quota_reported_6a147, "No quota reported")
    else -> appText(R.string.ui_not_available_d1a17, "Not available")
}

@Composable
internal fun LimitAccountRow(account: LimitAccount, displayOptions: DisplayOptions) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(account.provider, Blue, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(limitDisplayProvider(account).ifBlank { appText(R.string.ui_provider_7ceee, "Provider") }.providerLabel(), color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val email = account.accountEmail.takeIf { displayOptions.showAccountEmails }.orEmpty()
                val source = account.sourceDeviceId.takeIf { displayOptions.showLimitSource && it.isNotBlank() }?.let { appText(R.string.ui_source_1_s_ab512, "Source %1\$s", it.displayName()) }
                val updated = account.updatedAt.relativeAge(LocalNow.current).takeIf { it.isNotBlank() }?.let { appText(R.string.ui_updated_1_s_62d23, "Updated %1\$s", it) }
                val meta = listOf(account.productLabel, account.accountName, account.plan, email, source, updated).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (account.windows.isEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(limitStatusLabel(account.status), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (account.windows.isNotEmpty()) {
            account.windows.chunked(2).forEach { windows ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    windows.forEach { window ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val remaining = window.remainingPercent ?: window.usedPercent?.let { 100.0 - it }
                            val usedPercent = window.usedPercent ?: window.remainingPercent?.let { 100.0 - it }
                            Row {
                                val additionalTokens = museAdditionalTokens(account, window)
                                Text(limitWindowTitle(account, window), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1)
                                val amount = limitReportedAmount(account.provider, window)
                                val display = additionalTokens ?: amount?.let { formatWindowAmount(it, window.currency) } ?: (if (displayOptions.limitBarMetric == LimitBarMetric.Remaining) {
                                    quotaPercentLeft(window)?.let { appText(R.string.ui_1_s_left_e96f9, "%1\$s%% left", it) }
                                } else {
                                    usedPercent?.let { appText(R.string.ui_1_s_used_f116e, "%1\$s used", formatPercent(it)) }
                                })
                                    ?: window.remaining?.let { formatWindowAmount(it, window.currency) }
                                    ?: appText(R.string.ui_not_reported_98e85, "Not reported")
                                Text(display, color = Ink, style = MaterialTheme.typography.labelSmall)
                            }
                            val balance = balanceMeterPercent(account.provider, window)
                            val meterPercent = if (displayOptions.limitBarMetric == LimitBarMetric.Remaining) remaining ?: balance else usedPercent ?: balance?.let { 100.0 - it }
                            val meter = (meterPercent ?: 0.0).coerceIn(0.0, 100.0).toFloat() / 100f
                            val risk = (((usedPercent ?: balance?.let { 100.0 - it }) ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat()
                            if (window.showMeter != false && meterPercent != null) UsageBar(meter, quotaColor(risk))
                            if (window.detail.isNotBlank()) Text(window.detail, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            formatBoundary(window.resetsAt, window.boundaryKind, LocalNow.current).takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        }
                    }
                    if (windows.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (io.github.theminionooo.tokenmonitor.data.storage.providerDetailKey(account.provider, "details") !in displayOptions.hiddenLimitItems) ProviderDetailRows(account)
        account.spend?.takeIf { io.github.theminionooo.tokenmonitor.data.storage.providerDetailKey(account.provider, "spend") !in displayOptions.hiddenLimitItems }?.let { spend ->
            limitSpendRows(spend).forEach { (label, amount) ->
                Text("$label ${formatWindowAmount(amount, spend.currency)}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (spend.locallyTracked && (spend.today != null || spend.week != null) && spend.trackingSince.isNotBlank()) {
                Text(appText(R.string.ui_tracked_since_1_s_3065e, "Tracked since %1\$s", spend.trackingSince), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider(color = Line)
    }
}
