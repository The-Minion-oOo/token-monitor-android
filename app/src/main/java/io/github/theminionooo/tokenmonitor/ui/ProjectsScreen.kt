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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
 * Projects and Sessions lists.
 */
internal fun LazyListScope.projectItems(projects: List<ProjectUsage>, period: DashboardPeriod, searching: Boolean = false) {
    if (projects.isEmpty()) item { MutedCopy(usageListEmptyMessage(false, period, searching), modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val maximum = projects.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
        val ranked = projects.sortedByDescending { it.totalTokens }
        val keys = uniqueKeys(ranked) { it.id }
        itemsIndexed(ranked, key = { index, _ -> keys[index] }) { _, project ->
            ProjectUsageRow(project, project.totalTokens.toFloat() / maximum)
        }
    }
}

@Composable
internal fun ProjectUsageRow(project: ProjectUsage, ratio: Float) {
    val expandable = project.totalTokens > 0
    var expanded by rememberSaveable(project.id) { mutableStateOf(false) }
    var modelShares by rememberSaveable(project.id) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (expandable) Modifier.clickable { expanded = !expanded } else Modifier,
        ).padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Purple, size = 10.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.label.ifBlank { project.id }, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = when {
                    project.clients.isNotEmpty() -> project.clients.keys.joinToString { it.displayName() }
                    project.sessionCount > 0 -> appText(R.string.ui_1_s_sessions_5ee6e, "%1\$s sessions", project.sessionCount)
                    else -> appText(R.string.ui_project_total_8d7d3, "Project total")
                }
                Text(meta, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                RowTokenValue(project.totalTokens)
                Text(displayUsageCost(project.costUsd, project.unpricedTokens), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        UsageBar(ratio, Purple)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceGroup(
                    options = listOf(appText(R.string.ui_tools_9d0e5, "TOOLS") to "tools", appText(R.string.ui_models_441ba, "MODELS") to "models"),
                    selected = if (modelShares) "models" else "tools",
                    onSelect = { modelShares = it == "models" },
                )
                projectShares(project, modelShares).forEach { share ->
                    Row(modifier = Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (share.unknown) StatusDot(Muted, size = 8.dp)
                        else if (modelShares) ModelMark(share.name, Purple, size = 8.dp)
                        else UpstreamToolMark(share.name, accentFor(share.name), size = 8.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(if (share.unknown || modelShares) share.name else share.name.displayName(), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text("${formatCompactTokens(share.tokens)} · ${formatPercent(share.tokens.toDouble() / project.totalTokens * 100)}", color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
    }
}

/**
 * [evidence] holds other periods' sessions, which can prove a Cursor conversation is a Grok Bot run. Search runs
 * after grouping, so a match keeps its whole group and its totals, and a group's own label can be searched.
 */
internal fun LazyListScope.sessionItems(
    sessions: List<SessionUsage>,
    period: DashboardPeriod,
    searching: Boolean = false,
    evidence: List<SessionUsage> = emptyList(),
    matches: (SessionUsage) -> Boolean = { true },
    query: String = "",
) {
    val ranked = searchSessionRows(groupSessions(sessions, evidence), searching, matches, query)
        .sortedByDescending { it.totalTokens }
    if (ranked.isEmpty()) item { MutedCopy(usageListEmptyMessage(true, period, searching), modifier = Modifier.padding(vertical = 12.dp)) }
    else {
        val maximum = ranked.maxOf { it.totalTokens }.coerceAtLeast(1L)
        val keys = uniqueKeys(ranked) { it.key }
        itemsIndexed(ranked, key = { index, _ -> keys[index] }) { _, row ->
            val ratio = row.totalTokens.toFloat() / maximum
            if (row.grouped) SessionGroupUsageRow(row, ratio)
            else sessionRowPresentation(row, LocalSessionTitles.current).let { SessionUsageRow(row.display, it.title, it.meta, ratio) }
        }
    }
}

/** A conversation with its subagents and continuations, or a Codex Auto Review or Grok Bot summary. */
@Composable
internal fun SessionGroupUsageRow(row: SessionRow, ratio: Float) {
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    val now = LocalNow.current
    // The desktop Sessions list shows activity on conversation groups, not on summary rows.
    val activity = if (row.kind == SessionGroupKind.Conversation) sessionRowActivity(row, now) else SessionActivityState.Idle
    val labels = sessionRowPresentation(row, LocalSessionTitles.current)
    val client = row.display.client
    Column(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(client, accentFor(client), size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(labels.title, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SessionActivityLine(activity, labels.meta)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                RowTokenValue(row.totalTokens)
                Text(displayUsageCost(row.costUsd, row.unpricedTokens), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) appText(R.string.ui_collapse_grouped_sessions_fcc39, "Collapse grouped sessions") else appText(R.string.ui_expand_grouped_sessions_ae201, "Expand grouped sessions"),
                tint = Muted,
                modifier = Modifier.size(16.dp).rotate(rememberChevronRotation(expanded)),
            )
        }
        UsageBar(ratio, accentFor(client))
        sessionRowMetricLabels(row, now).takeIf { it.isNotBlank() }?.let {
            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(modifier = Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                row.members.forEach { member -> SessionMemberLine(member, row.kind, now) }
            }
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
private fun SessionMemberLine(member: SessionMember, kind: SessionGroupKind?, now: Long) {
    val session = member.session
    val labels = sessionRowLabels(session, LocalSessionTitles.current)
    val role = if (kind == SessionGroupKind.Conversation) (if (member.main) appText(R.string.ui_main_session_36cd7, "Main session") else appText(R.string.ui_subagent_b639a, "Subagent")) else ""
    Row(verticalAlignment = Alignment.CenterVertically) {
        UpstreamToolMark(session.client, accentFor(session.client), size = 8.dp)
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(labels.title, color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SessionActivityLine(sessionActivityState(session, now), listOf(role, labels.meta).filter { it.isNotBlank() }.joinToString(" · "))
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTokens(session.totalTokens), color = Ink, style = MaterialTheme.typography.bodySmall)
            Text(displayUsageCost(session.costUsd, session.unpricedTokens), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        // Lines the figures up with the group's, which leave room for the chevron.
        Spacer(Modifier.width(20.dp))
    }
}

/** Running keeps the green dot; waiting for a reply gets an orange one and says so. */
@Composable
private fun SessionActivityLine(activity: SessionActivityState, meta: String) {
    val label = sessionActivityLabel(activity)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotBlank()) {
            StatusDot(
                when (activity) {
                    SessionActivityState.Running -> Success
                    SessionActivityState.Waiting -> Orange
                    else -> Muted
                },
                size = 6.dp,
            )
            Spacer(Modifier.width(5.dp))
        }
        val waiting = OrangeText
        Text(
            buildAnnotatedString {
                if (activity == SessionActivityState.Waiting) withStyle(SpanStyle(color = waiting)) { append(label) } else append(label)
                if (label.isNotBlank() && meta.isNotBlank()) append(" · ")
                append(meta)
            },
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SessionUsageRow(session: SessionUsage, title: String, meta: String, ratio: Float) {
    var expanded by rememberSaveable(session.client, session.id) { mutableStateOf(false) }
    val motionEnabled = LocalInteractionMotion.current
    val now = LocalNow.current
    val activity = sessionActivityState(session, now)
    val context = sessionContextForRow(session, now)
    Column(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpstreamToolMark(session.client, accentFor(session.client), size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SessionActivityLine(activity, meta)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                RowTokenValue(session.totalTokens)
                Text(displayUsageCost(session.costUsd, session.unpricedTokens), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) appText(R.string.ui_collapse_session_details_ad28d, "Collapse session details") else appText(R.string.ui_expand_session_details_24b9f, "Expand session details"),
                tint = Muted,
                modifier = Modifier.size(16.dp).rotate(rememberChevronRotation(expanded)),
            )
        }
        UsageBar(ratio, accentFor(session.client))
        sessionMetricLabels(session, now).takeIf { it.isNotBlank() }?.let {
            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp))
        }
        context?.let { reading ->
            val contextColor = when {
                reading.percentLeft <= 10 -> DangerText
                reading.percentLeft <= 30 -> OrangeText
                else -> Muted
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appText(R.string.ui_context_1_s_used_03c26, "Context %1\$s%% used", reading.percentUsed), color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(appText(R.string.ui_1_s_left_e96f9, "%1\$s%% left", reading.percentLeft), color = contextColor, style = MaterialTheme.typography.labelSmall)
            }
            UsageBar(reading.percentUsed / 100f, contextColor)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(if (motionEnabled) 160 else 0)) + expandVertically(tween(if (motionEnabled) 240 else 0, easing = DesktopEaseOut)),
            exit = fadeOut(tween(if (motionEnabled) 100 else 0)) + shrinkVertically(tween(if (motionEnabled) 180 else 0, easing = DesktopEaseOut)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (session.projectLabel.isNotBlank()) DetailLine(appText(R.string.ui_project_f6f4d, "Project"), session.projectLabel)
                if (session.startedAt.isNotBlank()) DetailLine(appText(R.string.ui_started_faa9e, "Started"), session.startedAt.shortTime())
                if (session.modelNames.isNotEmpty()) DetailLine("Models", session.modelNames.joinToString { it.displayName() })
                DetailLine(appText(R.string.ui_session_f7f19, "Session"), session.id)
                if (session.dotsObservedOnly) Text(
                    appText(R.string.ui_dots_usage_covers_observations_while_the_desktop_was_91afa, "Dots usage covers observations while the desktop was connected, not complete history."),
                    color = Muted, style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "Prompt and reply text stays on the desktop and is not synchronized by the v${BuildConfig.UPSTREAM_VERSION} Hub.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    lineHeight = 15.sp,
                )
            }
        }
        HorizontalDivider(color = Line)
    }
}
