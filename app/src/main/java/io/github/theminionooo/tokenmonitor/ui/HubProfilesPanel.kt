package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.HubProfileSummary
import io.github.theminionooo.tokenmonitor.domain.HubConnection

internal class HubProfileActions(
    val add: (String, String, String, Boolean, Boolean, String) -> Unit,
    val pairQr: (HubConnection, String) -> Unit,
    val select: (String) -> Unit,
    val rename: (String, String) -> Unit,
    val remove: (String) -> Unit,
    val resetEditor: () -> Unit = {},
)

@Composable
internal fun HubProfilesPanel(state: HubRepositoryState, form: ConnectionFormState, actions: HubProfileActions, onAdd: (() -> Unit)?) {
    var editingId by remember { mutableStateOf<String?>(null) }
    val busy = form.saving
    SettingsGroup(appText(R.string.hub_profile_title, "Saved Hubs"), collapsible = false) {
        for (profile in state.profiles) {
            val active = profile.id == state.activeProfileId
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name.ifBlank { profile.address }, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (active) Text(appText(R.string.hub_profile_active, "Active Hub"), color = Accent, style = MaterialTheme.typography.bodySmall)
                    else if (profile.name.isNotBlank()) Text(profile.address, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!active) TextButton(onClick = { actions.select(profile.id) }, enabled = !busy) {
                    Text(appText(R.string.hub_profile_switch, "Switch"))
                }
                TextButton(onClick = { editingId = profile.id }, enabled = !busy) {
                    Text(appText(R.string.hub_profile_edit, "Edit"))
                }
            }
        }
        if (onAdd != null) OutlinedButton(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(appText(R.string.hub_profile_add, "Add Hub"))
        }
        if (form.profileUpdateFailed) {
            Text(appText(R.string.hub_profile_error, "Could not update saved Hubs. Try again."), color = DangerText)
        }
    }
    state.profiles.firstOrNull { it.id == editingId }?.let { profile ->
        EditHubProfile(profile, onDismiss = { editingId = null }, actions = actions)
    }
}

@Composable
private fun EditHubProfile(profile: HubProfileSummary, onDismiss: () -> Unit, actions: HubProfileActions) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var confirmingRemoval by remember(profile.id) { mutableStateOf(false) }
    val remove = appText(R.string.hub_profile_remove, "Remove Hub")
    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(remove) },
            text = { Text(appText(R.string.hub_profile_remove_confirm, "Remove the saved connection for %1\$s?", profile.name.ifBlank { profile.address })) },
            dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text(appText(R.string.ui_cancel_77dfd, "Cancel")) } },
            confirmButton = { TextButton(onClick = { actions.remove(profile.id); onDismiss() }) { Text(remove, color = DangerText) } },
        )
    } else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appText(R.string.hub_profile_edit, "Edit")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.address)
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(appText(R.string.hub_profile_name, "Hub name (optional)")) })
                TextButton(onClick = { confirmingRemoval = true }) { Text(remove, color = DangerText) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(appText(R.string.ui_cancel_77dfd, "Cancel")) } },
        confirmButton = {
            TextButton(onClick = { actions.rename(profile.id, name); onDismiss() }) {
                Text(appText(R.string.hub_profile_save, "Save"))
            }
        },
    )
}
