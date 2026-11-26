package io.github.theminionooo.tokenmonitor.domain

import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException

internal const val QUOTA_ALERT_THRESHOLD = 85
private const val REARM_BELOW = 80
private const val MAX_READ_AGE_SECONDS = 15 * 60L
private const val MAX_BASELINE_AGE_SECONDS = 24 * 60 * 60L
private const val CLOCK_SKEW_SECONDS = 60L

internal enum class QuotaAlert { Low, Reset }

internal data class QuotaReading(val at: Instant, val used: Double, val resetsAt: Instant?, val low: Boolean)

/** Compares provider measurements within a single active Hub. Missing or ambiguous windows start over. */
internal class QuotaAlertDetector(saved: Map<String, QuotaReading> = emptyMap()) {
    var readings: Map<String, QuotaReading> = saved
        private set

    fun clear() { readings = emptyMap() }

    fun observe(accounts: List<LimitAccount>, now: Instant): Set<QuotaAlert> {
        val incoming = accounts.flatMap { account ->
            val at = account.updatedAt.instantOrNull()
            if (at == null || account.status != "ok" || account.stale ||
                at < now.minusSeconds(MAX_READ_AGE_SECONDS) || at > now.plusSeconds(CLOCK_SKEW_SECONDS)) {
                return@flatMap emptyList()
            }
            account.windows.mapNotNull { window ->
                val used = window.usedPercent ?: window.remainingPercent?.let { 100.0 - it }
                if (used == null || used !in 0.0..100.0 || window.showMeter == false ||
                    account.provider.isBlank() || window.kind.isBlank()) return@mapNotNull null
                val key = windowKey(account.provider, account.accountKey, account.sourceDeviceId, account.source,
                    account.region, window.productId.ifBlank { account.productLabel }, window.kind, window.label, window.metric)
                key to QuotaReading(at, used, window.resetsAt.instantOrNull(), used >= QUOTA_ALERT_THRESHOLD)
            }
        }.groupBy({ it.first }, { it.second })
        val changes = mutableSetOf<QuotaAlert>()
        val next = mutableMapOf<String, QuotaReading>()
        for ((key, matches) in incoming) {
            val reading = matches.singleOrNull() ?: continue
            val previous = readings[key]?.takeIf { it.at >= now.minusSeconds(MAX_BASELINE_AGE_SECONDS) }
            if (previous == null) {
                next[key] = reading
                continue
            }
            if (reading.at <= previous.at) {
                next[key] = previous
                continue
            }
            val reset = previous.resetsAt != null && reading.resetsAt != null &&
                reading.at >= previous.resetsAt &&
                reading.resetsAt > reading.at && reading.used < previous.used
            if (reset) changes += QuotaAlert.Reset
            if (!reset && !previous.low && reading.low) changes += QuotaAlert.Low
            next[key] = reading.copy(low = reading.low || (!reset && previous.low && reading.used >= REARM_BELOW))
        }
        readings = next
        return changes
    }

    private fun windowKey(vararg fields: String): String {
        // Keep only a fingerprint of identity fields in the durable baseline.
        val encoded = buildString { fields.forEach { append(it.length).append(':').append(it) } }
        return MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).toHexString()
    }

    private fun String.instantOrNull(): Instant? = try {
        if (isEmpty()) null else Instant.parse(this)
    } catch (_: DateTimeParseException) { null }
}
