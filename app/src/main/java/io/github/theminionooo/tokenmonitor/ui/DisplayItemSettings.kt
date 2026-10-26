package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot

@Composable
internal fun DisplayItemSettings(snapshot: HubSnapshot?, options: DisplayOptions, onChange: (DisplayOptions) -> Unit) {
    SettingsGroup(appText(R.string.ui_tools_and_quotas_0b13a, "Tools and quotas"), summary = appText(R.string.ui_local_visibility_and_order_eb2af, "Local visibility and order")) {
        Text(appText(R.string.ui_these_choices_change_this_phone_s_display_collection_90959, "These choices change this phone's display. Collection and the Hub's totals stay the same."), color = Muted, style = MaterialTheme.typography.bodySmall)
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(query, { query = it }, label = { Text(appText(R.string.ui_find_a_tool_or_provider_dc602, "Find a tool or provider")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        val allTools = snapshot?.stats?.periods?.values.orEmpty().flatMap { it.clients.keys }.distinct().sorted()
        val toolOrder = options.toolOrder.filter { it in allTools } + allTools.filterNot { it in options.toolOrder }
        toolOrder.filter { query.isBlank() || it.displayName().contains(query, true) }.forEach { tool ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tool.displayName(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(tool !in options.hiddenTools, { show -> onChange(options.copy(hiddenTools = if (show) options.hiddenTools - tool else options.hiddenTools + tool)) })
                }
                Row {
                    TextButton(onClick = { onChange(options.copy(pinnedTools = if (tool in options.pinnedTools) options.pinnedTools - tool else options.pinnedTools + tool)) }) { Text(if (tool in options.pinnedTools) appText(R.string.ui_unpin_2eba6, "Unpin") else appText(R.string.ui_pin_9c918, "Pin")) }
                    TextButton(onClick = { onChange(options.copy(toolOrder = moveDisplayItem(toolOrder, tool, -1))) }, enabled = toolOrder.firstOrNull() != tool) { Text(appText(R.string.ui_move_up_b4f57, "Move up")) }
                    TextButton(onClick = { onChange(options.copy(toolOrder = moveDisplayItem(toolOrder, tool, 1))) }, enabled = toolOrder.lastOrNull() != tool) { Text(appText(R.string.ui_move_down_260ff, "Move down")) }
                }
            }
        }
        val accounts = snapshot?.stats?.limits?.providers.orEmpty()
        accounts.groupBy { it.provider }.filterKeys { query.isBlank() || it.providerLabel().contains(query, true) }.forEach { (provider, entries) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.providerLabel(), color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(provider !in options.hiddenProviders, { show -> onChange(options.copy(hiddenProviders = if (show) options.hiddenProviders - provider else options.hiddenProviders + provider)) })
            }
            val windows = entries.flatMap { it.windows }.distinctBy { limitItemKey(provider, it) }.map { limitItemKey(provider, it) to windowTitle(it, entries.flatMap { account -> account.windows }) }
            val extras = buildList {
                if (entries.any { it.resetCredits != null || it.usageSummary != null }) add(providerDetailKey(provider, "details") to appText(R.string.ui_quota_details_a9a13, "Quota details"))
                if (entries.any { it.spend != null }) add(providerDetailKey(provider, "spend") to appText(R.string.ui_spend_14eee, "Spend"))
            }
            (windows + extras).forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Switch(id !in options.hiddenLimitItems, { show -> onChange(options.copy(hiddenLimitItems = if (show) options.hiddenLimitItems - id else options.hiddenLimitItems + id)) }, enabled = provider !in options.hiddenProviders)
                }
            }
        }
        if (snapshot == null) Text(appText(R.string.ui_connect_to_a_hub_to_choose_its_tools_and_quota_items_f3e70, "Connect to a Hub to choose its tools and quota items."), color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange(options.copy(hiddenTools = emptySet(), pinnedTools = emptyList(), toolOrder = emptyList(), hiddenProviders = emptySet(), hiddenLimitItems = emptySet())) }) { Text(appText(R.string.ui_restore_display_defaults_a5260, "Restore display defaults")) }
    }
}
