package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ThroughputCounters
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod

internal data class LiveModelRate(val model: String, val outputPerSecond: Double, val tokensPerMinute: Double, val observedAt: Long)
internal data class LiveRateReading(val models: List<LiveModelRate>) {
    val outputPerSecond get() = models.sumOf { it.outputPerSecond }
    val tokensPerMinute get() = models.sumOf { it.tokensPerMinute }
    val expiresAt get() = (models.minOfOrNull { it.observedAt } ?: 0) + 8_000
}

/** Ratios use matched counter deltas, never elapsed watcher time or lifetime averages. */
internal class LiveThroughputTracker {
    private var previous = emptyMap<String, ThroughputCounters>()
    private var lastAt: Long? = null
    private val samples = linkedMapOf<String, LiveModelRate>()

    fun offer(period: UsagePeriod, now: Long): LiveRateReading? {
        if (!period.throughputAvailable) { previous = emptyMap(); samples.clear(); lastAt = null; return null }
        val current = period.modelThroughput.ifEmpty {
            mapOf("" to ThroughputCounters(period.timedTokens, period.timedOutputTokens, period.timedDurationMs))
        }
        if (lastAt?.let { now < it || now - it > 30_000 } == true) { previous = emptyMap(); samples.clear() }
        current.forEach { (model, value) ->
            val old = previous[model]
            if (old != null) {
                val tokens = value.timedTokens - old.timedTokens
                val output = value.timedOutputTokens - old.timedOutputTokens
                val duration = value.timedDurationMs - old.timedDurationMs
                if (tokens < 0 || output < 0 || duration < 0) samples.remove(model)
                else if (duration > 0 && (tokens > 0 || output > 0)) {
                    val speed = output.toDouble() * 1000 / duration
                    val burn = tokens.toDouble() * 60_000 / duration
                    if (speed.isFinite() && burn.isFinite()) samples[model] = LiveModelRate(model, speed, burn, now)
                }
            }
        }
        previous = current
        lastAt = now
        return reading(now)
    }

    fun reading(now: Long): LiveRateReading? {
        samples.entries.removeAll { (model, rate) -> model !in previous || now - rate.observedAt >= 8_000 || now < rate.observedAt }
        return samples.values.toList().takeIf { it.isNotEmpty() }?.let(::LiveRateReading)
    }
}
