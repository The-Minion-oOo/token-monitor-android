package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formatters are built once; they are used on every frame while a total rolls.
private val wholeNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US)
private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val twoDecimalFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
private val dateTimeFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMM d, h:mm a", AppLanguage.locale())
private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
internal val dayFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE, MMM d", AppLanguage.locale())

internal fun formatTokens(tokens: Long): String = wholeNumberFormat.format(tokens)

private val compactUnits = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")

/** [tokens] in tenths of [unit], rounded half up in whole numbers so the unit check below sees the rounded value. */
private fun tenthsOf(tokens: Long, unit: Long): Long {
    val step = unit / 10
    return tokens / step + if (tokens % step >= step / 2) 1 else 0
}

internal fun formatCompactTokens(tokens: Long): String {
    if (tokens < 10_000L) return formatTokens(tokens)
    var index = compactUnits.indexOfLast { tokens >= it.first }
    var tenths = tenthsOf(tokens, compactUnits[index].first)
    // A value that rounds up to 1000.0 of a unit belongs to the next one: 999,960 is 1.0M, not 1000.0K.
    while (tenths >= 10_000L && index < compactUnits.lastIndex) {
        index++
        tenths = tenthsOf(tokens, compactUnits[index].first)
    }
    return "${tenths / 10}.${tenths % 10}${compactUnits[index].second}"
}

internal fun formatMoney(value: Double): String = currencyFormat.format(value)

/** A missing catalog price is not a zero rate. Keep the known subtotal explicit. */
internal fun formatUsageCost(value: Double, unpricedTokens: Long = 0, compact: Boolean = false,
    currency: io.github.theminionooo.tokenmonitor.data.storage.UsageCurrency = io.github.theminionooo.tokenmonitor.data.storage.UsageCurrency()): String {
    val converted = value * currency.rate
    val amount = if (currency.code == "USD") formatMoney(value) else if (converted.isFinite()) NumberFormat.getCurrencyInstance(Locale.US).apply {
        this.currency = java.util.Currency.getInstance(currency.code)
    }.format(converted) else appText(R.string.format_not_reported, "Not reported")
    if (unpricedTokens <= 0) return amount
    val known = if (value > 0) amount else "—"
    if (compact) return if (value > 0) "$known + ?" else "— (?)"
    val missing = appText(R.string.format_unpriced, "%1\$s unpriced", formatCompactTokens(unpricedTokens))
    return if (value > 0) "$known + $missing" else "— ($missing)"
}

internal fun formatSubscriptionAmount(amountMinor: Long, currency: String): String = if (currency == "USD") {
    formatMoney(amountMinor / 100.0)
} else {
    "$currency ${wholeNumberFormat.format(amountMinor / 100.0)}"
}

/**
 * Balances and spend in their reported currency, as the desktop prints them: `$` and `¥` symbols, other codes
 * spelled out, provider credits bare, always to the cent. An amount without a currency is a plain count.
 */
internal fun formatWindowAmount(value: Double, currency: String): String = when (val code = currency.trim().uppercase(Locale.US)) {
    "" -> wholeNumberFormat.format(value)
    "USD" -> formatMoney(value)
    "CNY" -> "¥${twoDecimalFormat.format(value)}"
    "CREDITS" -> twoDecimalFormat.format(value)
    else -> "$code ${twoDecimalFormat.format(value)}"
}

/** The desktop `formatDuration`: `2d 21h`, `1h 59m`, `5m`, or `<1m`, rounded to the minute. */
internal fun formatDuration(milliseconds: Long): String {
    val totalMinutes = Math.round(milliseconds.coerceAtLeast(0L) / 60_000.0)
    val days = totalMinutes / 1_440
    val hours = (totalMinutes % 1_440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/** The desktop `formatActiveDuration`: total hours and minutes, such as `542h 28m`. */
internal fun formatActiveDuration(milliseconds: Long): String {
    val totalMinutes = Math.round(milliseconds.coerceAtLeast(0L) / 60_000.0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** The desktop `formatReset`: a countdown such as `Reset 1h 59m`, `Reset now` once due, or nothing without a timestamp. */
internal fun formatReset(resetsAt: String, now: Long): String {
    val target = runCatching { Instant.parse(resetsAt).toEpochMilli() }.getOrNull() ?: return ""
    val remaining = target - now
    return if (remaining <= 0) appText(R.string.format_reset_now, "Reset now") else appText(R.string.format_reset, "Reset %1\$s", formatDuration(remaining))
}

/** v0.56 keeps one timestamp but tells readers whether it resets, expires, or does both. */
internal fun formatBoundary(resetsAt: String, boundaryKind: String, now: Long): String {
    val target = runCatching { Instant.parse(resetsAt).toEpochMilli() }.getOrNull() ?: return ""
    val remaining = target - now
    val suffix = if (remaining <= 0) appText(R.string.format_now, "now") else formatDuration(remaining)
    return when (boundaryKind.lowercase()) {
        "expiry" -> appText(R.string.format_expires, "Expires %1\$s", suffix)
        "mixed" -> if (remaining <= 0) appText(R.string.format_changes_now, "Changes now") else appText(R.string.format_changes, "Changes in %1\$s", suffix)
        else -> appText(R.string.format_reset, "Reset %1\$s", suffix)
    }
}

/** Desktop-style age such as `38s ago`, `5m ago`, `2h 10m ago`, or `3d 4h ago`. */
internal fun formatRelativeAge(timestamp: Long, now: Long): String {
    if (timestamp <= 0) return ""
    val age = (now - timestamp).coerceAtLeast(0L)
    return when {
        age < 5_000 -> appText(R.string.format_just_now, "just now")
        age < 60_000 -> appText(R.string.format_seconds_ago, "%1\$ss ago", age / 1_000)
        else -> appText(R.string.format_duration_ago, "%1\$s ago", formatDuration(age))
    }
}

internal fun String.relativeAge(now: Long): String =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()?.let { formatRelativeAge(it, now) }.orEmpty()

internal fun formatPercent(value: Double): String = "${value.toInt()}%"

internal fun formatLiveRate(value: Double): String = String.format(AppLanguage.locale(), "%.1f", value)

internal fun formatHubBuild(value: String): String {
    if (value.isBlank()) return appText(R.string.format_not_reported, "Not reported")
    val normalized = value.removePrefix("sha256:")
    return if (normalized.length > 14) "${normalized.take(14)}…" else normalized
}

internal fun formatCapturedAt(timestamp: Long): String = if (timestamp <= 0) appText(R.string.format_unknown_time, "an unknown time") else runCatching {
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
}.getOrDefault(appText(R.string.format_recently, "recently"))

internal fun String.shortTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
}.getOrDefault("")

internal fun String.shortClockTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(clockFormat)
}.getOrDefault("")

internal fun shortDate(value: String): String = runCatching {
    LocalDate.parse(value.take(10)).format(shortDateFormat)
}.getOrDefault(value.take(5))

/** The desktop renderer's tool labels, keyed by the Hub's client id. */
private val toolLabels = mapOf(
    "claude" to "Claude Code", "codex" to "Codex", "hermes" to "Hermes Agent", "gemini" to "Gemini", "cursor" to "Cursor",
    "opencode" to "OpenCode", "openclaw" to "OpenClaw", "antigravity" to "Antigravity", "cline" to "Cline", "kimi" to "Kimi",
    "amp" to "Amp", "droid" to "Factory Droid", "qwen" to "Qwen", "grok" to "Grok Build", "copilot" to "GitHub Copilot",
    "pi" to "Pi", "omp" to "Oh My Pi", "senpi" to "OmO Native", "zed" to "Zed", "kilo" to "Kilo", "kilocode" to "Kilo Code", "commandcode" to "Command Code",
    "mimo" to "Xiaomi MiMo", "micode" to "Xiaomi MiMo", "zcode" to "ZCode", "kiro" to "Kiro", "codebuddy" to "CodeBuddy",
    "workbuddy" to "WorkBuddy", "proma" to "Proma", "catpaw" to "CatPaw", "qodercn" to "Qoder CN", "reasonix" to "Reasonix", "dsh" to "DeepSeek Harness",
    "cherrystudio" to "Cherry Studio", "lmstudio" to "LM Studio", "unsloth" to "Unsloth", "devin" to "Devin", "openrouter" to "OpenRouter",
    "deepseek" to "DeepSeek", "ollama" to "Ollama", "thirdparty" to "Third-party",
    "factory" to "Factory", "nvidia" to "NVIDIA", "stepfun" to "StepFun", "muse" to "Muse Code", "fx" to "fx",
    "mcode" to "MiniMax Code", "goose" to "Goose",
    "manus" to "Manus",
)

/**
 * Tool ids get the desktop's label, model ids stay exactly as the Hub reports them
 * (the desktop prints `gpt-5.6-sol`, not `Gpt-5.6-sol`), and anything else is title-cased.
 */
internal fun String.displayName(): String {
    val key = trim().lowercase(Locale.US)
    toolLabels[key]?.let { return it }
    if (any { it.isDigit() } || contains('/')) return trim()
    return providerName()
}

/** The desktop's period words for a limit window kind, used when two windows share a name. */
internal fun windowPeriodLabel(kind: String): String = when (kind.trim().lowercase(Locale.US)) {
    "session" -> "5-hour"
    "daily" -> "Daily"
    "weekly" -> "Weekly"
    "monthly" -> "Monthly"
    "billing" -> "Billing"
    else -> kind.providerName()
}

/**
 * A window's row title. Codex reports separately metered quotas such as GPT-5.3-Codex-Spark
 * as two windows with the same label and different kinds, so when a label repeats within an
 * account the period is appended: `GPT-5.3-Codex-Spark · 5-hour` and `GPT-5.3-Codex-Spark · Weekly`.
 */
internal fun windowTitle(window: io.github.theminionooo.tokenmonitor.domain.LimitWindow, siblings: List<io.github.theminionooo.tokenmonitor.domain.LimitWindow>): String {
    val base = window.label.ifBlank { window.kind.ifBlank { "Limit" } }.displayName()
    val shared = siblings.count { it !== window && it.label.ifBlank { it.kind.ifBlank { "Limit" } }.displayName() == base } > 0
    return if (shared && window.kind.isNotBlank()) "$base · ${windowPeriodLabel(window.kind)}" else base
}

/** Plain title case for provider and platform names, where the desktop shows `Claude` rather than `Claude Code`. */
internal fun String.providerName(): String = replace('_', ' ').split(' ').joinToString(" ") { part ->
    part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
}

internal val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)

/** Limit and subscription providers: the desktop shows `Claude`, not `Claude Code`, but keeps labels such as `OpenCode`. */
private val providerLabels = mapOf(
    "claude" to "Claude",
    "factory" to "Factory Droid",
    "grok" to "Grok",
    "mimo" to "Xiaomi MiMo",
    "zai" to "GLM",
    "zaiteam" to "GLM Team",
    "qoder" to "Qoder",
    "devin" to "Devin",
    "typesafe" to "TypeSafe",
    "volcengine" to "Volcengine",
    "trae" to "Trae CN",
    "alibaba" to "Alibaba Cloud",
    "thirdparty" to "Third-party APIs",
    "notion" to "Notion AI",
    "muse" to "Muse", "muse-code" to "Muse Code", "muse-web" to "Muse",
)

internal fun String.providerLabel(): String {
    val key = trim().lowercase(Locale.US)
    return providerLabels[key] ?: displayName()
}

/** Display products separately while saved visibility choices retain the Hub's provider id. */
internal fun limitDisplayProvider(account: io.github.theminionooo.tokenmonitor.domain.LimitAccount): String =
    if (account.provider == "muse") {
        if (account.source == "web") "muse-web" else "muse-code"
    } else account.provider

/**
 * Lazy list keys must be unique, but Hub ids can repeat (two devices each reporting the same signed-out provider).
 * The first occurrence keeps its plain key so saved expanded state stays attached to it; repeats get `#2`, `#3`, ….
 */
internal fun <T> uniqueKeys(items: List<T>, key: (T) -> String): List<String> {
    val used = HashSet<String>()
    val repeats = HashMap<String, Int>()
    return items.map { item ->
        val base = key(item)
        var candidate = base
        while (!used.add(candidate)) {
            val occurrence = (repeats[base] ?: 1) + 1
            repeats[base] = occurrence
            candidate = "$base#$occurrence"
        }
        candidate
    }
}

/** Muse web state follows its product across devices; CLI observations stay device-local. */
internal fun limitAccountIdentity(account: io.github.theminionooo.tokenmonitor.domain.LimitAccount): String =
    account.provider + ":" + account.productLabel + ":" + account.accountKey.ifBlank {
        listOf(account.accountName, account.accountEmail, account.productLabel).joinToString(":")
    } + if (account.provider == "muse") {
        if (account.source == "web") ":source:web" else ":device:${account.sourceDeviceId}"
    } else ""
