package io.github.theminionooo.tokenmonitor.data

import org.junit.Assert.*
import org.junit.Test

class AliasReadGateTest {
    @Test fun `an answered revision is read once`() {
        val gate = AliasReadGate(retryMs = 60_000)
        assertTrue(gate.shouldRead(1, 0))
        gate.started(1)
        assertFalse("Stream events with the same revision do not repeat the read", gate.shouldRead(1, 10_000_000))
        assertTrue("A changed revision is read", gate.shouldRead(2, 0))
    }

    @Test fun `a failed read retries the same revision after the throttle`() {
        val gate = AliasReadGate(retryMs = 60_000)
        gate.started(4)
        gate.failed(1_000)
        assertFalse(gate.shouldRead(4, 60_999))
        assertTrue(gate.shouldRead(4, 61_000))
        gate.started(4)
        assertFalse("A successful retry settles the revision again", gate.shouldRead(4, 200_000))
    }

    @Test fun `reset allows an immediate read and a failure without a read is ignored`() {
        val gate = AliasReadGate(retryMs = 60_000)
        gate.failed(0)
        assertTrue(gate.shouldRead(1, 0))
        gate.started(1)
        gate.failed(0)
        gate.reset()
        assertTrue(gate.shouldRead(1, 1))
    }
}
