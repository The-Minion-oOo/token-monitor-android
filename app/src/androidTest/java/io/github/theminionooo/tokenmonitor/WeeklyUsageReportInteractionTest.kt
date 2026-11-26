package io.github.theminionooo.tokenmonitor

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.LocalInteractionMotion
import io.github.theminionooo.tokenmonitor.ui.LocalNow
import io.github.theminionooo.tokenmonitor.ui.SampleUsageButton
import io.github.theminionooo.tokenmonitor.ui.appText
import io.github.theminionooo.tokenmonitor.ui.sampleUsageHistory
import io.github.theminionooo.tokenmonitor.ui.trendItems
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeeklyUsageReportInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun trendsOpensAReportForSavedReadingsWithIncompleteCoverage() {
        val date = LocalDate.parse("2026-11-20")
        val noon = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = sampleUsageHistory(date).filterIndexed { index, _ -> index != 2 }
        compose.setContent {
            CompositionLocalProvider(LocalNow provides noon, LocalInteractionMotion provides false) {
                LazyColumn { trendItems(HubSnapshot(fromCache = true), history) }
            }
        }

        val title = appText(R.string.usage_report_weekly, "Weekly usage report")
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText(appText(R.string.usage_report_cached, "Based on saved Hub readings.")).assertIsDisplayed()
        compose.onNodeWithText(appText(R.string.usage_report_coverage, "%1\$s of %2\$s days recorded", 6, 7)).assertIsDisplayed()
        compose.onAllNodesWithText(appText(R.string.usage_report_sample, "Sample data")).fetchSemanticsNodes().let {
            check(it.isEmpty())
        }
    }

    @Test
    fun sampleReportSharesAReadablePngThroughTheAppProvider() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "usage-reports")
        val existing = directory.listFiles().orEmpty().toSet()
        compose.setContent { SampleUsageButton() }
        fun tap(string: Int, fallback: String) = compose.onNodeWithText(appText(string, fallback)).performClick()
        tap(R.string.usage_report_try_sample, "Try sample data")
        tap(R.string.usage_report_weekly, "Weekly usage report")
        compose.onAllNodesWithText(appText(R.string.usage_report_sample, "Sample data"))
            .fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }

        val chooser = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_CHOOSER),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null),
            true,
        )
        try {
            tap(R.string.ui_share_09ca5, "Share")
            compose.waitUntil(10_000) { chooser.hits == 1 }
            val file = directory.listFiles().orEmpty().single { it !in existing }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
            assertEquals("image/png", context.contentResolver.getType(uri))
            val bitmap = checkNotNull(context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream))
            try {
                assertTrue(bitmap.width >= 200 && bitmap.height >= 250)
                val colors = mutableSetOf<Int>()
                for (y in 0 until bitmap.height step 8) {
                    for (x in 0 until bitmap.width step 8) colors += bitmap.getPixel(x, y)
                }
                assertTrue("The share contains the drawn report", colors.size > 8)
            } finally {
                bitmap.recycle()
            }
        } finally {
            instrumentation.removeMonitor(chooser)
            directory.listFiles().orEmpty().filter { it !in existing }.forEach { it.delete() }
        }
    }
}
