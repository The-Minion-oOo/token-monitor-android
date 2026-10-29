package io.github.theminionooo.tokenmonitor.data.network

/** An opening snapshot alone must not erase earlier reconnect failures. */
internal class StreamConnectionHealth(private val openedAt: Long) {
    private var receivedEvent = false
    private var progressed = false

    fun observe(sourceType: String) {
        if (receivedEvent && sourceType in setOf("stats", "delta", "message")) progressed = true
        receivedEvent = true
    }

    fun healthy(now: Long): Boolean = progressed || now - openedAt >= 30_000L
}
