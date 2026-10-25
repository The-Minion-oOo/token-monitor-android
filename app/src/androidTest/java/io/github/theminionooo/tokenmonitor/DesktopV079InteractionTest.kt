package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesktopV079InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun stats(name: String) = HubProtocolParser.decodeStats(instrumentation.context.assets
        .open("protocol/v0.79.0/$name.json").bufferedReader().use { it.readText() })
    private fun capture(name: String) {
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun omoNativeIsDistinctAndKeepsItsToolSelectionIdentity() {
        val period = stats("omo-native").periods.getValue("today")
        var selected = ""
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    LazyColumn(Modifier.background(palette.shell).padding(16.dp)) {
                        breakdownItems(period.clients, period.clientCosts, emptyMap(), emptyMap(), emptyMap(), "No usage",
                            RankingMetric.Tokens, onToolSelected = { selected = it })
                    }
                }
            }
        }
        compose.onNodeWithText("OmO Native").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("senpi", selected) }
        compose.onNodeWithText("Pi").assertIsDisplayed()
        compose.onNodeWithText("Oh My Pi").assertIsDisplayed()
        compose.onNodeWithText("Senpi").assertDoesNotExist()
        capture("v079-omo-native")
    }

    @Test fun openCodeBalancesDoNotBecomeQuotaPercentages() {
        var account by mutableStateOf(stats("opencode-console").limits.providers.single())
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Column(Modifier.background(palette.shell).padding(16.dp)) { LimitAccountRow(account, DisplayOptions()) }
                }
            }
        }
        for (value in listOf("75% left", "60% left", "90% left")) compose.onNodeWithText(value).assertIsDisplayed()
        compose.onNodeWithText(formatWindowAmount(27.86781005, "USD")).assertIsDisplayed()
        capture("v079-opencode-console")
        for ((name, amount) in listOf("opencode-zero" to 0.0, "opencode-negative" to -1.0)) {
            compose.runOnIdle { account = stats(name).limits.providers.single() }
            compose.onNodeWithText(formatWindowAmount(amount, "USD")).assertIsDisplayed()
            compose.onNodeWithText("75% left").assertDoesNotExist()
            compose.onNodeWithText("0% left").assertDoesNotExist()
            compose.onNodeWithText("100% left").assertDoesNotExist()
        }
    }
}
