package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.SessionUsage

/** Raw accounting totals for a row; ratios are calculated only after disjoint members are summed. */
internal data class SessionMetrics(
    val messageCount: Long,
    val inputTokens: Double,
    val cacheReadTokens: Double,
    val cacheWriteTokens: Double,
    val timedOutputTokens: Double,
    val timedDurationMs: Double,
) {
    val cacheHitPercent: Double? get() {
        if (cacheReadTokens <= 0 && cacheWriteTokens <= 0) return null
        val input = inputTokens + cacheReadTokens + cacheWriteTokens
        return if (input > 0) cacheReadTokens * 100 / input else null
    }
    val tokenRate: Double? get() = if (timedOutputTokens > 0 && timedDurationMs > 0)
        timedOutputTokens * 1000 / timedDurationMs else null
}

internal fun sessionMetrics(sessions: List<SessionUsage>): SessionMetrics = SessionMetrics(
    messageCount = sessions.sumOf { it.messageCount.coerceAtLeast(0).toLong() },
    inputTokens = sessions.sumOf { it.inputTokens.coerceAtLeast(0).toDouble() },
    cacheReadTokens = sessions.sumOf { it.cacheReadTokens.coerceAtLeast(0).toDouble() },
    cacheWriteTokens = sessions.sumOf { it.cacheWriteTokens.coerceAtLeast(0).toDouble() },
    // Clamp each member before summing: untimed output cannot borrow a sibling's duration.
    timedOutputTokens = sessions.sumOf { if (it.timedDurationMs > 0)
        minOf(it.outputTokens, it.timedOutputTokens).coerceAtLeast(0).toDouble() else 0.0 },
    timedDurationMs = sessions.sumOf { it.timedDurationMs.coerceAtLeast(0).toDouble() },
)

internal fun sessionRowMetricLabels(row: SessionRow, now: Long): String {
    if (!row.grouped) return sessionMetricLabels(row.display, now)
    return listOfNotNull(
        "Dots · observed only".takeIf { row.sessions.any { it.dotsObservedOnly } },
        sessionCounterLabels(row.usageMetrics.cacheHitPercent, row.usageMetrics.tokenRate).takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}
