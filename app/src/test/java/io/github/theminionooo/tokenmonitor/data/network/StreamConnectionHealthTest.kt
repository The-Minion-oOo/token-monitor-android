package io.github.theminionooo.tokenmonitor.data.network

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StreamConnectionHealthTest {
    @Test fun `snapshot then disconnect keeps exponential backoff across connections`() {
        val backoff = BackoffPolicy(Random(1))
        var attempt = 0
        val delays = (0..4).map {
            val connection = StreamConnectionHealth(1000)
            connection.observe("snapshot")
            if (connection.healthy(1001)) attempt = 0
            backoff.delayForAttempt(attempt++)
        }
        assertTrue(delays.last() >= 12_800)
        assertTrue(delays.last() > delays.first() * 8)
    }

    @Test fun `only later stats delta or legacy messages establish progress`() {
        for (kind in listOf("snapshot", "stats", "message")) {
            val connection = StreamConnectionHealth(0)
            connection.observe(kind)
            assertFalse(connection.healthy(1))
            connection.observe("freshness")
            assertFalse(connection.healthy(2))
            connection.observe("delta")
            assertTrue(connection.healthy(3))
        }
        for (later in listOf("stats", "message")) {
            val connection = StreamConnectionHealth(0)
            connection.observe("snapshot"); connection.observe(later)
            assertTrue(connection.healthy(1))
        }
    }

    @Test fun `a quiet connection resets backoff only after thirty seconds`() {
        val connection = StreamConnectionHealth(10_000)
        assertFalse(connection.healthy(39_999))
        assertTrue(connection.healthy(40_000))
    }
}
