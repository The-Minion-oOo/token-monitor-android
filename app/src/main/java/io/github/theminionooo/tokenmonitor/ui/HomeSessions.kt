package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.SessionUsage
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home is a recent-session summary, independent of the selected usage period. Like the desktop it groups
 * subagents and continuations under their conversation, keeps the chosen number of newest rows and never hides a row
 * that is running or waiting for a reply.
 */
internal fun recentHomeSessions(snapshot: HubSnapshot, now: Long, count: Int = 5): List<SessionRow> =
    visibleHomeSessions(groupHomeSessions(snapshot.month.sessions, snapshot.today.sessions), now, count)

internal fun groupHomeSessions(month: List<SessionUsage>, today: List<SessionUsage>): List<SessionRow> {
    val all = month + today
    val sessions = all.filter { it.sessionKind != "background-review" }
        .distinctBy { it.client to it.id }
        .filter { session ->
            runCatching { Instant.parse(session.lastUsedAt.ifBlank { session.startedAt }) }.isSuccess
        }
    return groupSessions(sessions, evidence = all)
        .sortedByDescending { it.sortTime }
}

internal fun visibleHomeSessions(grouped: List<SessionRow>, now: Long, count: Int = 5): List<SessionRow> =
    grouped.filterIndexed { index, row ->
        index < count.coerceIn(1, 50) || sessionRowActivity(row, now) in setOf(SessionActivityState.Running, SessionActivityState.Waiting)
    }

/** Decoder reconciliation owns structural comparisons; preparation keys only track new inputs. */
private class ReferenceKey(private val value: Any?) {
    override fun equals(other: Any?): Boolean = other is ReferenceKey && value === other.value
    override fun hashCode(): Int = System.identityHashCode(value)
}

/** Also keeps compiler-generated parameter and lambda comparisons off the session contents. */
internal class HomeSessionInputs(val month: List<SessionUsage>, val today: List<SessionUsage>) {
    override fun equals(other: Any?): Boolean = other is HomeSessionInputs && month === other.month && today === other.today
    override fun hashCode(): Int = 31 * System.identityHashCode(month) + System.identityHashCode(today)
}

@Composable
internal fun HomeSessionsModule(
    inputs: HomeSessionInputs,
    count: Int = 5,
    onChoose: (DashboardDestination) -> Unit,
) {
    val now = LocalNow.current
    var grouped by remember { mutableStateOf<List<SessionRow>?>(null, referentialEqualityPolicy()) }
    LaunchedEffect(inputs) {
        grouped = withContext(Dispatchers.Default) { groupHomeSessions(inputs.month, inputs.today) }
    }
    val rows = remember(ReferenceKey(grouped), now, count) { visibleHomeSessions(grouped.orEmpty(), now, count) }
    val states = rows.map { sessionRowActivity(it, now) }
    val waiting = states.count { it == SessionActivityState.Waiting }
    val meta = listOfNotNull(appText(R.string.ui_1_s_running_0175a, "%1\$s running", states.count { it == SessionActivityState.Running }), waiting.takeIf { it > 0 }?.let { appText(R.string.ui_1_s_waiting_837d2, "%1\$s waiting", it) })
        .joinToString(" · ")
    DesktopModule(appText(R.string.ui_sessions_72114, "SESSIONS"), DashboardDestination.Sessions, onChoose, meta = meta.takeIf { grouped != null }) {
        if (grouped == null) {
            MutedCopy(appText(R.string.ui_loading_sessions_31640, "Loading sessions…"))
        } else if (rows.isEmpty()) {
            MutedCopy(appText(R.string.ui_no_recent_sessions_reported_by_the_hub_dc276, "No recent sessions reported by the Hub"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEachIndexed { index, row ->
                    val session = row.display
                    val state = states[index]
                    val age = Instant.ofEpochMilli(row.sortTime).toString().relativeAge(now)
                    val members = when (row.kind) {
                        SessionGroupKind.Conversation -> row.subagentCount.takeIf { it > 0 }?.let { if (it == 1) appText(R.string.ui_1_subagent_cdb57, "1 subagent") else appText(R.string.ui_1_s_subagents_74d31, "%1\$s subagents", it) }
                        SessionGroupKind.GrokBot -> appText(R.string.ui_1_s_activity_records_2cade, "%1\$s activity records", row.members.size)
                        else -> null
                    }
                    Column {
                        HomeListRow(
                            name = sessionRowPresentation(row, LocalSessionTitles.current).title,
                            primary = formatCompactTokens(row.totalTokens),
                            secondary = listOfNotNull(session.client.displayName(), sessionActivityLabel(state).ifBlank { appText(R.string.ui_idle_cc1eb, "Idle") }, members, age, session.projectLabel)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = when (state) {
                                SessionActivityState.Running -> Success
                                SessionActivityState.Waiting -> Orange
                                SessionActivityState.Finished -> Blue
                                SessionActivityState.Idle -> Muted
                            },
                            literalName = true,
                        )
                        sessionContextForRow(session, now)?.let { context ->
                            Text(appText(R.string.ui_context_1_s_left_15dbf, "Context %1\$s%% left", context.percentLeft), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                        sessionRowMetricLabels(row, now).takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
