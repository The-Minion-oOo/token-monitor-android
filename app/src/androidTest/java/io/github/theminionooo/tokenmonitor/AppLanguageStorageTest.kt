package io.github.theminionooo.tokenmonitor

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.ui.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageStorageTest {
    @Test fun selectionSurvivesActivityRecreationAndCanReturnToSystemLanguage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = AppLanguage.selected(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { AppLanguage.choose(it, "te") }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity {
                    assertEquals("te", AppLanguage.selected(it))
                    assertEquals("te", it.resources.configuration.locales[0].language)
                }
                scenario.onActivity { AppLanguage.choose(it, "") }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity { assertEquals("", AppLanguage.selected(it)) }
            }
        } finally {
            instrumentation.runOnMainSync { AppLanguage.choose(context, original) }
            AppLanguage.bind(context)
        }
    }
}
