package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.domain.ProjectUsage

internal data class ProjectShare(val name: String, val tokens: Long, val unknown: Boolean = false)

/** Bound each reported map to the project total; missing attribution stays unknown, never estimated. */
internal fun projectShares(project: ProjectUsage, models: Boolean): List<ProjectShare> {
    var remaining = project.totalTokens.coerceAtLeast(0)
    val shares = (if (models) project.models else project.clients).toSortedMap().mapNotNull { (name, value) ->
        val tokens = value.coerceIn(0, remaining)
        if (name.isBlank() || tokens == 0L) null else ProjectShare(name, tokens).also { remaining -= tokens }
    }
    return (shares + if (remaining > 0) listOf(ProjectShare(if (models) appText(R.string.ui_unknown_model_450cb, "Unknown model") else appText(R.string.ui_unknown_tool_1468c, "Unknown tool"), remaining, true)) else emptyList())
        .sortedByDescending { it.tokens }
}
