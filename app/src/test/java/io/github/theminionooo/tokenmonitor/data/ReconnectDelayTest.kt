package io.github.theminionooo.tokenmonitor.data

import org.junit.Assert.*
import org.junit.Test

class ReconnectDelayTest {
    @Test fun `a notice waits three seconds and retries keep the original deadline`() {
        val delay = ReconnectDelay()
        assertNull(delay.remainingMillis(100))
        delay.failed(100)
        assertEquals(3_000L, delay.remainingMillis(100))
        delay.failed(1_100)
        assertEquals(1L, delay.remainingMillis(3_099))
        assertEquals(0L, delay.remainingMillis(3_100))
        delay.failed(5_000)
        assertEquals(0L, delay.remainingMillis(6_000))
    }

    @Test fun `fresh data cancels a short interruption and starts a separate outage`() {
        val delay = ReconnectDelay()
        delay.failed(1_000)
        delay.recovered()
        assertNull(delay.remainingMillis(5_000))
        delay.failed(6_000)
        assertEquals(3_000L, delay.remainingMillis(6_000))
    }
}
