package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import java.text.NumberFormat

internal data class ToolSpending(
    val costUsd: Double?,
    val costShare: Double?,
    val tokenMix: ToolTokenMix?,
)

internal data class ToolTokenMix(
    val input: Long,
    val cacheReads: Long,
    val cacheWrites: Long,
    val output: Long,
    val unclassified: Long,
)

internal fun UsagePeriod.toolSpending(tool: String, fromHistory: Boolean): ToolSpending {
    // History aggregation defaults an absent daily cost to zero. A positive subtotal
    // is usable, but an aggregated zero cannot distinguish free usage from missing cost.
    val cost = clientCosts[tool]?.takeIf { it.isFinite() && it >= 0 && (!fromHistory || it > 0) }
    val share = cost?.takeIf { costUsd.isFinite() && costUsd > 0 && it <= costUsd }
        ?.div(costUsd)
    return ToolSpending(cost, share, if (fromHistory) null else toolTokenMix(tool))
}

private fun UsagePeriod.toolTokenMix(tool: String): ToolTokenMix {
    val tokens = (clients[tool] ?: 0).coerceAtLeast(0)
    val unknown = (clientUnclassifiedTokens[tool] ?: 0).coerceIn(0, tokens)
    val reads = (clientCacheReads[tool] ?: 0).coerceIn(0, tokens - unknown)
    val writes = (clientCacheWrites[tool] ?: 0).coerceIn(0, tokens - unknown - reads)
    val output = (clientOutputs[tool] ?: 0).coerceIn(0, tokens - unknown - reads - writes)
    val remaining = tokens - unknown - reads - writes - output
    // An explicit unclassified count (including zero) identifies the remaining input.
    val input = if (tool in clientUnclassifiedTokens) remaining else 0
    return ToolTokenMix(input, reads, writes, output, tokens - reads - writes - output - input)
}

@Composable
internal fun ToolSpendingPanel(spending: ToolSpending) {
    Surface(
        color = Overlay,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val percent = spending.costShare?.let {
                val formatter = NumberFormat.getPercentInstance(AppLanguage.locale())
                formatter.maximumFractionDigits = 1
                formatter.format(it)
            }
            Text(
                if (percent == null) appText(R.string.ui_spending_share_unavailable, "Cost share unavailable")
                else appText(R.string.ui_spending_cost_share, "%1\$s of known estimated cost", percent),
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            spending.tokenMix?.let { mix ->
                SpendingComponent(appText(R.string.ui_input_cache_miss_08248, "Input (Cache Miss)"), mix.input)
                SpendingComponent(appText(R.string.ui_spending_cache_reads, "Cache reads"), mix.cacheReads)
                SpendingComponent(appText(R.string.ui_spending_cache_writes, "Cache writes"), mix.cacheWrites)
                SpendingComponent(appText(R.string.ui_output_4bed3, "Output"), mix.output)
                SpendingComponent(appText(R.string.ui_unclassified_390c4, "Unclassified"), mix.unclassified)
            }
        }
    }
}

@Composable
private fun SpendingComponent(label: String, tokens: Long) {
    if (tokens <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(formatTokens(tokens), color = Ink, style = MaterialTheme.typography.labelSmall)
    }
}
