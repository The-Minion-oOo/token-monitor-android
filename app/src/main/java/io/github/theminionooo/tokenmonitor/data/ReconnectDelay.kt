package io.github.theminionooo.tokenmonitor.data

import io.github.theminionooo.tokenmonitor.data.network.HubApiException
import io.github.theminionooo.tokenmonitor.data.network.hubTlsMessage

/** Retries belong to one outage until fresh data arrives. Uses the monotonic clock. */
internal class ReconnectDelay {
    private var failedAt: Long? = null

    fun failed(now: Long) {
        if (failedAt == null) failedAt = now
    }

    fun remainingMillis(now: Long): Long? = failedAt?.let { (3_000L - (now - it).coerceAtLeast(0)).coerceAtLeast(0) }

    fun recovered() {
        failedAt = null
    }
}

/**
 * The reason to show after the reconnect delay when retrying alone cannot fix the failure: certificates,
 * route and permission checks, refused redirects, oversized responses or a missing endpoint. Brief Hub
 * outages, timeouts and dropped connections return null and keep the generic reconnect notice.
 */
internal fun reconnectNoticeCause(error: Throwable): String? = when (error) {
    is HubApiException -> error.presentation.takeUnless { error.statusCode >= 500 || error.statusCode == 408 || error.statusCode == 429 }
    else -> hubTlsMessage(error)
}
