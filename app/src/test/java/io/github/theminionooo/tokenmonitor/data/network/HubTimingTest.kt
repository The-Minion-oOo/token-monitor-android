package io.github.theminionooo.tokenmonitor.data.network

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class HubTimingTest {
    @Test fun `disabled diagnostics do not read clocks or invoke the sink`() {
        val recorder = HubTimingRecorder(false, clock = { error("Clock must not be read") }, sink = { error("No events") })
        assertEquals(42, recorder.measure(HubTimingStage.REQUEST) { 42 })
        assertEquals(0, recorder.nextRequest())
    }

    @Test fun `timings retain fixed labels numbers and elapsed durations`() {
        val samples = mutableListOf<HubTimingSample>()
        val now = AtomicLong(10_000)
        val recorder = HubTimingRecorder(true, { now.get() }, samples::add)
        now.set(20_000)
        val value = recorder.measure(HubTimingStage.BODY, HubTimingEndpoint.STATS, recorder.nextRequest(), { 200 }, { 512 }) {
            now.set(50_000)
            "body is not captured"
        }
        assertEquals("body is not captured", value)
        assertEquals(30, samples.single().durationUs)
        assertEquals(40, samples.single().elapsedUs)
        assertEquals(512, samples.single().bytes)
        assertEquals(200, samples.single().status)
        assertFalse(samples.single().line().contains(value))
    }

    @Test fun `unknown paths and failure messages never enter output`() {
        val secret = "secret-address-account-and-payload"
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add)
        val failure = IllegalStateException(secret)
        try {
            recorder.measure(HubTimingStage.REQUEST, HubTimingEndpoint.fromPath("https://$secret/private")) { throw failure }
            fail("Expected the original failure")
        } catch (error: IllegalStateException) { assertSame(failure, error) }
        assertEquals(HubTimingEndpoint.OTHER, samples.single().endpoint)
        assertEquals(HubTimingOutcome.FAILED, samples.single().outcome)
        assertFalse(samples.single().line().contains(secret))
        assertFalse(samples.single().line().contains("https"))
    }

    @Test fun `cancellation and HTTP failures retain their original exceptions`() {
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add)
        val failures = listOf(CancellationException("private"), HubApiException(401, "private"), HubResponseTooLargeException())
        failures.forEach { failure ->
            try { recorder.measure(HubTimingStage.REQUEST) { throw failure } }
            catch (error: Exception) { assertSame(failure, error) }
        }
        assertEquals(listOf(HubTimingOutcome.CANCELLED, HubTimingOutcome.HTTP_ERROR, HubTimingOutcome.TOO_LARGE), samples.map { it.outcome })
        assertFalse(samples.joinToString { it.line() }.contains("private"))
    }

    @Test fun `a broken diagnostic sink cannot change a successful read`() {
        val recorder = HubTimingRecorder(true, sink = { error("Sink unavailable") })
        assertEquals("ok", recorder.measure(HubTimingStage.REQUEST) { "ok" })
    }

    @Test fun `fatal errors are rethrown and are never labelled as success`() {
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add)
        val failure = AssertionError("private failure detail")
        try { recorder.measure(HubTimingStage.REQUEST) { throw failure } }
        catch (error: AssertionError) { assertSame(failure, error) }
        assertEquals(HubTimingOutcome.FAILED, samples.single().outcome)
        assertFalse(samples.single().line().contains("private"))
    }

    @Test fun `a long session has a bounded log budget`() {
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add, maxEvents = 2)
        repeat(100) { assertEquals(it, recorder.measure(HubTimingStage.REQUEST) { it }) }
        assertEquals(2, samples.size)
    }
}
