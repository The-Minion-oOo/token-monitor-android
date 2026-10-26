package io.github.theminionooo.tokenmonitor.domain

/**
 * The only persisted connection target. The secret is never included in UI state or logs.
 * [fallbackUrl] is an optional private home address tried whenever [baseUrl] does not answer.
 */
data class HubConnection(
    val baseUrl: String,
    val secret: String,
    val allowLocalNetwork: Boolean,
    val fallbackUrl: String? = null,
    val allowPublicHttps: Boolean = false,
) {
    // The generated data-class text would print the secret into logs and crash reports.
    override fun toString() = "HubConnection(baseUrl=$baseUrl, secret=<redacted>, allowLocalNetwork=$allowLocalNetwork, fallbackUrl=$fallbackUrl, allowPublicHttps=$allowPublicHttps)"
}

data class HubHealth(
    val ok: Boolean = false,
    val role: String = "",
    val runtime: String = "",
    val hubBuild: String = "",
    val deviceCount: Int = 0,
    val secretRequired: Boolean = false,
    val now: String = "",
)

data class UsagePeriod(
    val throughputAvailable: Boolean = false,
    val timedTokens: Long = 0,
    val timedOutputTokens: Long = 0,
    val timedDurationMs: Long = 0,
    val totalTokens: Long = 0,
    val costUsd: Double = 0.0,
    val clients: Map<String, Long> = emptyMap(),
    val clientCosts: Map<String, Double> = emptyMap(),
    val models: Map<String, Long> = emptyMap(),
    val modelCosts: Map<String, Double> = emptyMap(),
    val clientModels: Map<String, Map<String, Long>> = emptyMap(),
    val clientModelCosts: Map<String, Map<String, Double>> = emptyMap(),
    val clientCacheReads: Map<String, Long> = emptyMap(),
    val clientCacheWrites: Map<String, Long> = emptyMap(),
    val clientOutputs: Map<String, Long> = emptyMap(),
    val clientUnclassifiedTokens: Map<String, Long> = emptyMap(),
    val modelCacheReads: Map<String, Long> = emptyMap(),
    val modelCacheWrites: Map<String, Long> = emptyMap(),
    val modelOutputs: Map<String, Long> = emptyMap(),
    val modelUnclassifiedTokens: Map<String, Long> = emptyMap(),
    val projects: List<ProjectUsage> = emptyList(),
    val sessions: List<SessionUsage> = emptyList(),
    val unpricedTokens: Long = 0,
    val clientUnpricedTokens: Map<String, Long> = emptyMap(),
    val modelUnpricedTokens: Map<String, Long> = emptyMap(),
    val clientModelUnpricedTokens: Map<String, Map<String, Long>> = emptyMap(),
)

data class ProjectUsage(
    val id: String,
    val label: String,
    val totalTokens: Long,
    val costUsd: Double,
    val sessionCount: Int,
    val clients: Map<String, Long>,
    val unpricedTokens: Long = 0,
    val models: Map<String, Long> = emptyMap(),
)

data class SessionUsage(
    val id: String,
    val title: String = "",
    val client: String,
    val projectLabel: String,
    val totalTokens: Long,
    val costUsd: Double,
    val modelNames: List<String>,
    val messageCount: Int,
    val startedAt: String,
    val lastUsedAt: String,
    val sessionKind: String = "",
    val contextTokens: Long = 0,
    val contextWindow: Long = 0,
    val turnEnded: Boolean? = null,
    val archived: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val timedOutputTokens: Long = 0,
    val timedDurationMs: Long = 0,
    val promptCache: PromptCache? = null,
    val unpricedTokens: Long = 0,
    val dotsObservedOnly: Boolean = false,
    /** Positive per-model tokens, so grouped rows and Grok Bot detection can read them (desktop v0.69). */
    val modelTokens: Map<String, Long> = emptyMap(),
    /** Codex native conversation id shared by continuation files; presentation-only identity. */
    val nativeSessionId: String = "",
    /** T3 Code App thread identity joining continuations and delegated runs. */
    val t3ThreadId: String = "",
    /** The session that started this subagent or background review, in [parentClient]'s id space. */
    val parentSessionId: String = "",
    /** Blank means the child's own client. */
    val parentClient: String = "",
    val liveActivity: LiveActivity? = null,
    /** Codex question waiting; null when the producer did not say. */
    val waitingForInput: Boolean? = null,
    /** Explicit Cursor Grok Bot marker; null when absent, so ids and models decide. */
    val grokBotSession: Boolean? = null,
)

/** A provider-specific estimate, not a guaranteed server-side expiry. */
data class PromptCache(val observedAt: String, val ttlSeconds: Long)

/** A short-lived provider observation: running, waiting, idle or unknown, valid for 30 seconds. */
data class LiveActivity(val state: String, val observedAt: String)

data class DeviceUsage(
    val id: String,
    val hostname: String,
    val platform: String,
    val osName: String,
    val osVersion: String,
    val updatedAt: String,
    val receivedAt: String,
    val ageMs: Long?,
    val stale: Boolean,
    val syncUploadIntervalMs: Long?,
    val historyAvailable: Boolean?,
    val history: HubHistory,
    val trackedClients: List<String>,
    val periods: Map<String, UsagePeriod>,
)

data class LimitWindow(
    val kind: String,
    val label: String,
    val usedPercent: Double?,
    val remainingPercent: Double?,
    val remaining: Double?,
    val resetsAt: String,
    val metric: String,
    val currency: String,
    val detail: String,
    val showMeter: Boolean?,
    val boundaryKind: String = "",
)

data class LimitAccount(
    val provider: String,
    val accountName: String,
    val accountEmail: String,
    val plan: String,
    val status: String,
    val sourceDeviceId: String,
    val updatedAt: String,
    val windows: List<LimitWindow>,
    val accountKey: String = "",
    val productLabel: String = "",
    val spend: LimitSpend? = null,
)

/** Provider-reported native-currency spend; daily/weekly values may be locally tracked. */
data class LimitSpend(
    val currency: String,
    val today: Double? = null,
    val week: Double? = null,
    val month: Double? = null,
    val allTime: Double? = null,
    val trackingSince: String = "",
    val locallyTracked: Boolean = true,
)

data class HubLimits(
    val updatedAt: String = "",
    val providers: List<LimitAccount> = emptyList(),
)

data class HistoryAttribution(
    val tokens: Long = 0,
    val costUsd: Double = 0.0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    val unclassifiedTokens: Long = 0,
    val unpricedTokens: Long = 0,
)

data class HistoryPoint(
    val label: String,
    val tokens: Long,
    val costUsd: Double,
    val messages: Long = 0,
    val activeTimeMs: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    val unclassifiedTokens: Long = 0,
    val tokenComponentsAvailable: Boolean = false,
    val perClient: Map<String, HistoryAttribution> = emptyMap(),
    val perModel: Map<String, HistoryAttribution> = emptyMap(),
    val unpricedTokens: Long = 0,
)

data class HubHistory(
    val daily: List<HistoryPoint> = emptyList(),
    val monthly: List<HistoryPoint> = emptyList(),
)

data class Subscription(
    val id: String,
    val provider: String,
    val planName: String,
    val amountMinor: Long,
    val currency: String,
    val startDate: String,
    val interval: String,
    val autoRenew: Boolean,
    val kind: String = "subscription",
    val intervalCount: Int = 1,
    val binding: SubscriptionBinding = SubscriptionBinding(),
    val topUps: List<SubscriptionTopUp> = emptyList(),
    val nextRenewalOverride: String = "",
    val endDate: String = "",
    val note: String = "",
    val amountReported: Boolean = true,
)

data class SubscriptionBinding(val profileName: String = "", val accountKey: String = "", val accountEmail: String = "")
data class SubscriptionTopUp(val id: String, val date: String, val amountMinor: Long)

data class HubSubscriptions(
    val updatedAt: String = "",
    val entries: List<Subscription> = emptyList(),
)

data class HubStats(
    val updatedAt: String = "",
    val periods: Map<String, UsagePeriod> = emptyMap(),
    val devices: List<DeviceUsage> = emptyList(),
    val limits: HubLimits = HubLimits(),
    val historyPreview: HubHistory = HubHistory(),
    val subscriptionsUpdatedAt: String = "",
    val staleAfterMs: Long? = null,
)

data class HubSnapshot(
    val health: HubHealth = HubHealth(),
    val stats: HubStats = HubStats(),
    val history: HubHistory = HubHistory(),
    val subscriptions: HubSubscriptions = HubSubscriptions(),
    val capturedAt: Long = 0L,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
) {
    val today: UsagePeriod get() = stats.periods["today"] ?: UsagePeriod()
    val month: UsagePeriod get() = stats.periods["month"] ?: UsagePeriod()
    val allTime: UsagePeriod get() = stats.periods["allTime"] ?: UsagePeriod()
}
