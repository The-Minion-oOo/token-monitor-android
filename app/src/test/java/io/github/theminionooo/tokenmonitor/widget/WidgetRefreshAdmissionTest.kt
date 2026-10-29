package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.data.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WidgetRefreshAdmissionTest {
    @Test fun `every skipped run returns before repository acquisition or cache work`() = runBlocking {
        for (scenario in 0..5) {
            val actions = mutableListOf<String>()
            runAdmittedWidgetRefresh(
                admit = {
                    actions += "admit"
                    widgetRefreshAdmission(
                        WidgetRefreshSettings(if (scenario == 0) WidgetRefreshInterval.Off else WidgetRefreshInterval.Thirty, true),
                        widgets = { scenario != 1 }, rejected = { scenario == 2 },
                        busy = { scenario == 3 || scenario == 4 }, wifiAvailable = { scenario != 5 },
                    )
                },
                acquire = { actions += "decrypt/cache"; Any() },
                release = { actions += "release" }, refresh = { actions += "refresh" },
            )
            assertEquals(listOf("admit"), actions)
        }
    }

    @Test fun `off does not even query widgets or networks and a run always releases its owner`() = runBlocking {
        assertEquals(WidgetRefreshDecision.Cancel, widgetRefreshAdmission(WidgetRefreshSettings(),
            { error("Off") }, { error("Off") }, { error("Off") }, { error("Off") }))
        val actions = mutableListOf<String>()
        try {
            runAdmittedWidgetRefresh({ WidgetRefreshDecision.Run }, { actions += "acquire"; Any() },
                { actions += "release" }, { actions += "run"; throw IllegalStateException("synthetic failure") })
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("acquire", "run", "release"), actions)
    }

    @Test fun `deadline and wifi loss are failures rather than silent admission cancellations`() {
        val run = WidgetRefreshRun(45_000)
        run.beforeRequest(44_999, true)
        for (check in listOf({ run.beforeRequest(45_000, true) }, { run.beforeRequest(1, false) })) {
            val failure = assertThrows(Exception::class.java) { check() }
            assertFalse(failure is CancellationException)
            assertFalse(run.completed)
            assertTrue(WidgetRefreshRecord(successAt = 1, failureAt = 45_000).stale(1))
        }
    }

    @Test fun `applied data stays successful if later work exceeds the deadline`() {
        val run = WidgetRefreshRun(45_000)
        run.applied()
        assertThrows(WidgetRefreshFailure::class.java) { run.beforeRequest(45_001, true) }
        assertTrue(run.completed)
    }
}
