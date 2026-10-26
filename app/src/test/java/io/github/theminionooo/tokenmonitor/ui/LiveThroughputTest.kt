package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.ThroughputCounters
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod
import org.junit.Assert.*
import org.junit.Test

class LiveThroughputTest {
    private fun period(total: Long, output: Long, duration: Long) = UsagePeriod(throughputAvailable = true, timedTokens = total, timedOutputTokens = output, timedDurationMs = duration)
    @Test fun `live deltas use matched performance time rather than watcher time and metadata cannot prolong activity`() {
        val tracker = LiveThroughputTracker()
        assertNull(tracker.offer(period(10000, 4000, 100000), 0))
        val reading = tracker.offer(period(10300, 4100, 102000), 5000)!!
        assertEquals(50.0, reading.outputPerSecond, 0.0)
        assertEquals(9000.0, reading.tokensPerMinute, 0.0)
        assertEquals(5000L, tracker.offer(period(10300, 4100, 102000), 10000)!!.models.single().observedAt)
        assertNull(tracker.reading(13000))
    }
    @Test fun `regressed counters unavailable capability and long reconnects reset the baseline`() {
        val tracker = LiveThroughputTracker()
        tracker.offer(period(10, 5, 10), 0)
        assertNotNull(tracker.offer(period(20, 10, 20), 1))
        assertNull(tracker.offer(period(1, 1, 1), 2))
        assertNull(tracker.offer(period(300, 100, 300), 40000))
        assertNull(tracker.offer(UsagePeriod(), 40001))
    }
    @Test fun `concurrent model rates add their individually matched samples`() {
        val tracker = LiveThroughputTracker()
        val start = UsagePeriod(throughputAvailable = true, modelThroughput = mapOf("a" to ThroughputCounters(10, 5, 1000), "b" to ThroughputCounters(20, 5, 1000)))
        tracker.offer(start, 0)
        val next = start.copy(modelThroughput = mapOf("a" to ThroughputCounters(50, 25, 2000), "b" to ThroughputCounters(80, 35, 3000)))
        val reading = tracker.offer(next, 1000)!!
        assertEquals(35.0, reading.outputPerSecond, 0.0)
        assertEquals(4200.0, reading.tokensPerMinute, 0.0)
        assertEquals(2, reading.models.size)
    }
}
