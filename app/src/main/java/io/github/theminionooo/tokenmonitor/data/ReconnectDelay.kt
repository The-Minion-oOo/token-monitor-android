package io.github.theminionooo.tokenmonitor.data

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
