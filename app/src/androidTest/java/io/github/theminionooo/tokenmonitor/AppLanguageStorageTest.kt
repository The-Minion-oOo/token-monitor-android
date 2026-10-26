package io.github.theminionooo.tokenmonitor

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.theminionooo.tokenmonitor.ui.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageStorageTest {
    @Test fun selectionSurvivesActivityRecreationAndCanReturnToSystemLanguage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val original = AppLanguage.selected(context)
        val originalLanguage = context.resources.configuration.locales[0].language
        val systemLanguage = android.content.res.Resources.getSystem().configuration.locales[0].language
        fun awaitLanguage(language: String) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any { it.resources.configuration.locales[0].language == language }
                }
                if (ready) return
                android.os.SystemClock.sleep(50)
            }
            throw AssertionError("The recreated activity did not apply $language")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { AppLanguage.choose(it, "") }
                awaitLanguage(systemLanguage)
                scenario.onActivity { AppLanguage.choose(it, "te") }
                // Both the framework and the legacy picker recreate automatically. Wait for
                // that transition instead of racing it with a second explicit recreate().
                awaitLanguage("te")
                scenario.onActivity {
                    assertEquals("te", AppLanguage.selected(it))
                    assertEquals("te", it.resources.configuration.locales[0].language)
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals("te", it.resources.configuration.locales[0].language) }
                scenario.onActivity { AppLanguage.choose(it, "") }
                awaitLanguage(systemLanguage)
                scenario.onActivity { assertEquals("", AppLanguage.selected(it)) }
            }
        } finally {
            instrumentation.runOnMainSync { AppLanguage.choose(context, original) }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                while (context.resources.configuration.locales[0].language != originalLanguage && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
            }
            AppLanguage.bind(context)
        }
    }
}
