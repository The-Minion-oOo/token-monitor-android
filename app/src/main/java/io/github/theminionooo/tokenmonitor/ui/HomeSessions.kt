package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import java.time.Instant

/**
 * Home is a recent-session summary, independent of the selected usage period. Like the desktop it groups
 * subagents and continuations under their conversation, keeps the five newest rows and never hides a row
 * that is running or waiting for a reply.
 */
internal fun recentHomeSessions(snapshot: HubSnapshot, now: Long): List<SessionRow> {
    val all = snapshot.month.sessions + snapshot.today.sessions
    val sessions = all.filter { it.sessionKind != "background-review" }
        .distinctBy { it.client to it.id }
        .filter { session ->
            runCatching { Instant.parse(session.lastUsedAt.ifBlank { session.startedAt }) }.isSuccess
        }
    return groupSessions(sessions, evidence = all)
        .sortedByDescending { it.sortTime }
        .filterIndexed { index, row ->
            index < 5 || sessionRowActivity(row, now) in setOf(SessionActivityState.Running, SessionActivityState.Waiting)
        }
}

@Composable
internal fun HomeSessionsModule(snapshot: HubSnapshot, onChoose: (DashboardDestination) -> Unit) {
    val now = LocalNow.current
    val rows = recentHomeSessions(snapshot, now)
    val states = rows.map { sessionRowActivity(it, now) }
    val waiting = states.count { it == SessionActivityState.Waiting }
    val meta = listOfNotNull("${states.count { it == SessionActivityState.Running }} running", waiting.takeIf { it > 0 }?.let { "$it waiting" })
        .joinToString(" · ")
    DesktopModule("SESSIONS", DashboardDestination.Sessions, onChoose, meta = meta) {
        if (rows.isEmpty()) {
            MutedCopy("No recent sessions reported by the Hub")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEachIndexed { index, row ->
                    val session = row.display
                    val state = states[index]
                    val age = Instant.ofEpochMilli(row.sortTime).toString().relativeAge(now)
                    val members = when (row.kind) {
                        SessionGroupKind.Conversation -> row.subagentCount.takeIf { it > 0 }?.let { if (it == 1) "1 subagent" else "$it subagents" }
                        SessionGroupKind.GrokBot -> "${row.members.size} activity records"
                        else -> null
                    }
                    Column {
                        HomeListRow(
                            name = sessionRowPresentation(row, LocalSessionTitles.current).title,
                            primary = formatCompactTokens(row.totalTokens),
                            secondary = listOfNotNull(session.client.displayName(), sessionActivityLabel(state).ifBlank { "Idle" }, members, age, session.projectLabel)
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
                            Text("Context ${context.percentLeft}% left", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                        sessionMetricLabels(session, now).takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
