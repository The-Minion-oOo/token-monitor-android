package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DashboardLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        instrumentation.targetContext.openFileOutput("$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun denseModelsAndDeviceGroupsRemainReadable() {
        fun asset(name: String) = instrumentation.context.assets.open("protocol/v0.54.0/$name").bufferedReader().use { it.readText() }
        val source = HubProtocolParser.decodeSnapshot(asset("health.json"), asset("stats.json"), asset("devices.json"), asset("history.json"), asset("subscriptions.json"), System.currentTimeMillis(), false)
        val models = linkedMapOf("gpt-5.6-sol" to 2_150_735_172L, "gpt-5.6-terra" to 676_510_412L, "gpt-5.5" to 360_503_617L, "claude-fable-5-1" to 199_044_378L, "claude-fable-5" to 51_992_544L, "gpt-5.6-luna" to 36_395_438L)
        val usage = source.today.copy(totalTokens = 3_612_552_439, models = models, modelCosts = models.mapValues { it.value / 1e6 }, modelCacheReads = emptyMap(), modelOutputs = emptyMap())
        val device = source.stats.devices.first().copy(hostname = "Demo workstation", periods = mapOf("today" to usage))
        val snapshot = source.copy(history = HubHistory(daily = listOf(HistoryPoint(LocalDate.now().toString(), usage.totalTokens, 2500.0, perModel = models.mapValues { HistoryAttribution(it.value, it.value / 1e6) }, perClient = mapOf("codex" to HistoryAttribution(3_000_000_000L, 2000.0), "claude" to HistoryAttribution(600_000_000L, 500.0))))), stats = source.stats.copy(periods = mapOf("today" to usage), devices = listOf(device)))
        var destination by mutableStateOf(DashboardDestination.Models)
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Box(Modifier.width(393.dp).fillMaxHeight().background(palette.shell)) {
                        DashboardContent(Modifier.fillMaxSize(), HubRepositoryState(snapshot = snapshot), destination, DashboardPeriod.Today, { destination = it }, {}, false, ServiceStatusState(), DisplayOptions(), {})
                    }
                }
            }
        }
        compose.onNodeWithText("gpt-5.6-luna").assertIsDisplayed()
        save("models")
        compose.runOnIdle { destination = DashboardDestination.Devices }
        compose.onNodeWithText("Demo workstation").performClick()
        compose.onNodeWithText("TOP MODELS ON THIS DEVICE").assertIsDisplayed()
        save("device")
        compose.runOnIdle { destination = DashboardDestination.Trends }
        compose.onNodeWithText("NEXT DAY").performScrollTo().assertIsNotEnabled()
        save("trends")
    }

    @Test fun compactExplorerFiltersAndUsesThemedDetails() {
        val today = LocalDate.now().toString()
        val entries = (1..8).associate { "demo-model-$it" to HistoryAttribution(it * 1000L, it.toDouble()) }
        val history = listOf(HistoryPoint(today, 36000, 36.0, perModel = entries))
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette) {
                    Column(Modifier.width(393.dp).background(palette.shell).padding(14.dp)) { UsageExplorer(history) }
                }
            }
        }
        compose.onNodeWithText("demo-model-1").assertIsDisplayed()
        save("explorer")
        compose.onNodeWithContentDescription("Find a tool or model").performTextInput("model-8")
        compose.onNodeWithText("demo-model-1").assertDoesNotExist()
        compose.onNodeWithText("demo-model-8").performClick()
        compose.onNodeWithText("8,000 tokens across 1 recorded day").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").assertIsDisplayed()
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        instrumentation.targetContext.openFileOutput("details.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithContentDescription("Find a tool or model").assertIsDisplayed()
    }

    @Test fun digitRollKeepsTheSettledLayoutAndReading() {
        val palette = Palette.from(InterfaceTheme.Default)
        var usage by mutableStateOf(UsagePeriod(totalTokens = 1_234_567, costUsd = 1.0))
        var row by mutableLongStateOf(98_765L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides true) {
                    Column(Modifier.width(393.dp).background(palette.shell).padding(14.dp)) {
                        TotalPanel(usage, compact = false)
                        Row {
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                RowTokenValue(row)
                                androidx.compose.material3.Text("cost line")
                            }
                        }
                        androidx.compose.material3.Text("below")
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        fun bounds(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val reading = compose.onNodeWithText("1,234,567").fetchSemanticsNode().id
        val below = bounds("below")
        val cost = bounds("cost line")
        compose.runOnIdle {
            usage = usage.copy(totalTokens = 1_299_999)
            row = 101_234L
        }
        // Mid-roll the reading keeps one accessibility node with the new value, and nothing around it moves.
        compose.mainClock.advanceTimeBy(150)
        org.junit.Assert.assertEquals(reading, compose.onNodeWithText("1,299,999").fetchSemanticsNode().id)
        compose.onNodeWithText("101,234").assertExists()
        org.junit.Assert.assertEquals(below, bounds("below"))
        org.junit.Assert.assertEquals(cost, bounds("cost line"))
        compose.mainClock.advanceTimeBy(1_000)
        org.junit.Assert.assertEquals(reading, compose.onNodeWithText("1,299,999").fetchSemanticsNode().id)
        org.junit.Assert.assertEquals(below, bounds("below"))
        org.junit.Assert.assertEquals(cost, bounds("cost line"))
    }
}
