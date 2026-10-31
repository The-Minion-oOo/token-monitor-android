package io.github.theminionooo.tokenmonitor.data.network

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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

    @Test fun `suspended spans preserve results and record time across resumption`() = runBlocking {
        val samples = mutableListOf<HubTimingSample>()
        val now = AtomicLong(10_000)
        val recorder = HubTimingRecorder(true, { now.get() }, samples::add)
        assertEquals(42, recorder.measureSuspending(HubTimingStage.SNAPSHOT) {
            yield()
            now.set(80_000)
            42
        })
        assertEquals(70, samples.single().durationUs)
        assertEquals(HubTimingOutcome.OK, samples.single().outcome)
        val failure = CancellationException("private")
        try { recorder.measureSuspending(HubTimingStage.SNAPSHOT) { yield(); throw failure } }
        catch (error: CancellationException) { assertSame(failure, error) }
        assertEquals(HubTimingOutcome.CANCELLED, samples.last().outcome)
        assertFalse(samples.joinToString { it.line() }.contains("private"))
    }

    @Test fun `disabled suspended spans never read the clock or log`() = runBlocking {
        val recorder = HubTimingRecorder(false, clock = { error("Clock must not be read") }, sink = { error("No events") })
        assertEquals(42, recorder.measureSuspending(HubTimingStage.SNAPSHOT) { yield(); 42 })
    }

    @Test fun `disabled markers skip route classification clocks counters and sinks`() {
        val recorder = HubTimingRecorder(false, clock = { error("No clock") }, sink = { error("No sink") })
        recorder.mark(HubTimingStage.ROUTE_SELECTED, route = { error("No classification") })
        recorder.failure(IllegalStateException("private"), 1, 2)
        assertEquals(0, recorder.nextRun())
    }

    @Test fun `markers retain fixed route and reason labels without private failure data`() {
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add)
        val run = recorder.nextRun()
        recorder.mark(HubTimingStage.ROUTE_SELECTED, run, HubTimingReason.NETWORK_AVAILABLE,
            route = { HubTimingRoute.fromUrl("https://hub.example.com/private-secret") })
        recorder.failure(HubApiException(503, "private-secret https://private-address"), run, 2)
        assertEquals(run + 1, recorder.nextRun())
        assertEquals(HubTimingRoute.INTERNET_HTTPS, samples.first().route)
        assertEquals(HubTimingReason.NETWORK_AVAILABLE, samples.first().reason)
        assertEquals(0, samples.first().durationUs)
        assertEquals(503, samples.last().status)
        assertEquals(2, samples.last().attempt)
        assertEquals(HubTimingOutcome.HTTP_ERROR, samples.last().outcome)
        assertTrue(samples.all { it.run == run })
        assertFalse(samples.joinToString { it.line() }.contains("private"))
        assertFalse(samples.joinToString { it.line() }.contains("hub.example.com"))
        assertEquals(HubTimingRoute.TAILSCALE, HubTimingRoute.fromUrl("http://100.64.0.1:17321"))
        assertEquals(HubTimingRoute.HOME, HubTimingRoute.fromUrl("http://192.168.1.2:17321"))
    }

    @Test fun `cancelled retry timing preserves planned delay and the same run`() = runBlocking {
        val samples = mutableListOf<HubTimingSample>()
        val clock = AtomicLong(0)
        val recorder = HubTimingRecorder(true, clock::get, samples::add)
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            recorder.measureSuspending(HubTimingStage.RETRY_WAIT, run = 7, attempt = 3, plannedMs = 8000) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        clock.set(50_000_000)
        job.cancelAndJoin()
        val sample = samples.single()
        assertEquals(7, sample.run)
        assertEquals(3, sample.attempt)
        assertEquals(8000, sample.plannedMs)
        assertEquals(50_000, sample.durationUs)
        assertEquals(HubTimingOutcome.CANCELLED, sample.outcome)
    }

    @Test fun `markers and spans share a budget and diagnostic failures remain harmless`() {
        val samples = mutableListOf<HubTimingSample>()
        val recorder = HubTimingRecorder(true, sink = samples::add, maxEvents = 2)
        recorder.mark(HubTimingStage.ROUTE_SELECTED, route = { error("private failure") })
        assertEquals(42, recorder.measure(HubTimingStage.REQUEST) { 42 })
        recorder.mark(HubTimingStage.ACTIVITY_RESUME, route = { error("Budget must skip this") })
        assertEquals(2, samples.size)
        assertEquals(HubTimingRoute.NONE, samples.first().route)
        HubTimingRecorder(true, sink = { error("private") }).mark(HubTimingStage.ACTIVITY_RESUME)
    }
}
