package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.SessionUsage

/** Cache tool choices by immutable session-list identity rather than comparing every record on the UI thread. */
internal class SessionToolInputs(val sessions: List<SessionUsage>) {
    override fun equals(other: Any?): Boolean = other is SessionToolInputs && sessions === other.sessions
    override fun hashCode(): Int = System.identityHashCode(sessions)
}

internal fun sessionToolIds(sessions: List<SessionUsage>): List<String> =
    sessions.map { it.client }.filter { it.isNotBlank() }.distinct().sorted()

internal fun sessionToolSelection(selected: String, tools: List<String>): String =
    selected.takeIf { tools.size > 1 && it in tools } ?: ""

/** Keep the whole conversation or summary when any of its members uses the selected tool. */
internal fun filterSessionRows(rows: List<SessionRow>, selected: String): List<SessionRow> =
    if (selected.isEmpty()) rows else rows.filter { row ->
        row.display.client == selected || row.members.any { it.session.client == selected }
    }

@Composable
internal fun SessionToolFilter(tools: List<String>, selected: String, onSelected: (String) -> Unit) {
    if (tools.size < 2) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf("") + tools, key = { it }) { client ->
            FilterChip(
                selected = selected == client,
                onClick = { onSelected(if (selected == client) "" else client) },
                label = { Text(if (client.isEmpty()) appText(R.string.ui_all_tools, "All tools") else client.displayName(), maxLines = 1) },
            )
        }
    }
}
