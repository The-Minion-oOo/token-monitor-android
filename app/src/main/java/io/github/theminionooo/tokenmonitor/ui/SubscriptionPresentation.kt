package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.Subscription
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal fun Subscription.isTopUp() = kind == "topup"

/** Calendar anchors match desktop: Jan 31 -> Feb 28 -> Mar 31, without accumulating drift. */
internal fun subscriptionBoundary(record: Subscription, today: LocalDate): LocalDate? {
    if (record.kind != "subscription") return null
    val end = runCatching { LocalDate.parse(record.endDate) }.getOrNull()
    if (end != null) return end
    val anchor = runCatching { LocalDate.parse(record.startDate) }.getOrNull() ?: return null
    val months = when (record.interval) { "month" -> record.intervalCount.toLong(); "year" -> record.intervalCount * 12L; else -> return null }
    if (months !in 1..1440) return null
    if (!record.autoRenew) return runCatching { anchor.plusMonths(months) }.getOrNull()
    val override = runCatching { LocalDate.parse(record.nextRenewalOverride) }.getOrNull()
    if (override != null && override >= today) return override
    val elapsed = ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(today)).coerceAtLeast(0)
    val base = runCatching { anchor.plusMonths(elapsed / months * months) }.getOrNull() ?: return null
    return if (base >= today) base else runCatching { anchor.plusMonths((elapsed / months + 1) * months) }.getOrNull()
}

internal fun topUpTotalMinor(record: Subscription, month: YearMonth? = null): Long? = runCatching {
    record.topUps.filter { month == null || it.date.startsWith(month.toString()) }
        .fold(0L) { total, payment -> Math.addExact(total, payment.amountMinor) }
}.getOrNull()

internal fun subscriptionCadence(record: Subscription): String = when {
    record.isTopUp() -> "Top-ups"
    record.kind != "subscription" -> "Payment record"
    record.interval !in setOf("month", "year") -> "Interval not reported"
    record.intervalCount == 1 -> if (record.interval == "year") "Yearly" else "Monthly"
    record.interval == "year" -> "Every ${record.intervalCount} years"
    else -> "Every ${record.intervalCount} months"
}
