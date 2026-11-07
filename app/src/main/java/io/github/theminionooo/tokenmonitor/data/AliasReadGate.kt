package io.github.theminionooo.tokenmonitor.data

/**
 * One alias read per advertised revision. A read that failed in transit may run again after [retryMs];
 * an answer that settled the revision, including an older Hub's missing route, is not repeated.
 */
internal class AliasReadGate(private val retryMs: Long = 60_000L) {
    private var revision: Long? = null
    private var retryAt: Long? = null

    fun shouldRead(revision: Long, now: Long): Boolean =
        this.revision != revision || retryAt?.let { now >= it } == true

    fun started(revision: Long) {
        this.revision = revision
        retryAt = null
    }

    fun failed(now: Long) {
        if (revision != null) retryAt = now + retryMs
    }

    fun reset() {
        revision = null
        retryAt = null
    }
}
