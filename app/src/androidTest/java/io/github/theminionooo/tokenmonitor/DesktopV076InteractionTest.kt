package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.domain.ProjectUsage
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopV076InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        instrumentation.targetContext.openFileOutput("v076-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun content(body: @Composable () -> Unit) {
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Column(Modifier.width(393.dp).fillMaxHeight().background(palette.shell).padding(16.dp)) { body() }
                }
            }
        }
    }

    @Test fun projectModelsToggleKeepsUnknownUsageAndLongLabelsReadable() {
        var project by mutableStateOf(ProjectUsage("sample", "Sample project", 100, 0.0, 0, mapOf("codex" to 100),
            models = mapOf("gemini-4-argon-with-a-long-model-name" to 60)))
        content { ProjectUsageRow(project, 1f) }
        compose.onNodeWithText("MODELS").assertDoesNotExist()
        compose.onNodeWithText("Sample project").performClick()
        compose.onNodeWithText("MODELS").performClick()
        compose.onNodeWithText("Unknown model").assertIsDisplayed()
        compose.onNodeWithText("60 · 60%").assertIsDisplayed()
        val labelBounds = compose.onNodeWithText("gemini-4-argon-with-a-long-model-name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val valueBounds = compose.onNodeWithText("60 · 60%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The long label must leave a gap before the value: $labelBounds / $valueBounds", valueBounds.left > labelBounds.right)
        save("project-models")
        compose.runOnIdle { project = project.copy(models = emptyMap()) }
        compose.onNodeWithText("100 · 100%").assertIsDisplayed()
        compose.onNodeWithText("Unknown model").assertIsDisplayed()
        save("project-legacy")
        compose.onNodeWithText("TOOLS").performClick()
        compose.onNodeWithText("Unknown model").assertDoesNotExist()
        compose.onNodeWithText("Codex", substring = false).assertIsDisplayed()
    }

    @Test fun homeCountChangesPersistAndLimitTheRenderedList() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("display_preferences", 0)
        val key = "home_count_Tools"
        val old = preferences.all[key]
        try {
            preferences.edit().remove(key).commit()
            val store = DisplayPreferences(context)
            assertEquals(5, store.options.value.homeListCount("Tools"))
            var count by mutableIntStateOf(5)
            content {
                HomeListCountControl("Tools", count) { count = it; store.setHomeListCount("Tools", it) }
                HomeBreakdown((1..6).associate { "sample-tool-$it" to it * 100L }, emptyMap(), RankingMetric.Tokens, count = count)
            }
            compose.onNodeWithText("sample-tool-2").assertIsDisplayed()
            compose.onNodeWithContentDescription("Tools items on Home").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithText("2 items").assertIsDisplayed()
            compose.onNodeWithText("sample-tool-4").assertDoesNotExist()
            compose.onNodeWithText("sample-tool-5").assertIsDisplayed()
            assertEquals(2, DisplayPreferences(context).options.value.homeListCount("Tools"))
            assertEquals(5, DisplayPreferences(context).options.value.homeListCount("Models"))
            save("home-count")
        } finally {
            preferences.edit().apply { if (old is Int) putInt(key, old) else remove(key) }.commit()
        }
    }

    @Test fun missingAntigravityQuotaDoesNotLookExhaustedOrAvailable() {
        val source = instrumentation.context.assets.open("protocol/v0.76.0/modern.json").bufferedReader().use { it.readText() }
        val account = HubProtocolParser.decodeStats(source).limits.providers.single()
        content { LimitAccountRow(account, DisplayOptions()) }
        compose.onNodeWithText("Gemini 4 Argon").assertIsDisplayed()
        compose.onNodeWithText("65% left").assertIsDisplayed()
        compose.onNodeWithText("0% left").assertIsDisplayed()
        compose.onNodeWithText("Not reported").assertIsDisplayed()
        compose.onNodeWithText("Available").assertDoesNotExist()
        save("antigravity-limits")
    }
}
