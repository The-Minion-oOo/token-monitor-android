package io.github.theminionooo.tokenmonitor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopV0841InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun accounts(name: String) = HubProtocolParser.decodeStats(InstrumentationRegistry.getInstrumentation().context.assets
        .open("protocol/v0.84.1/$name.json").bufferedReader().use { it.readText() }).limits.providers
    @Before fun englishCopy() { AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags("en"))
    })) }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) { File(context.filesDir, "$name.png").outputStream().use {
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    } }
    @Composable private fun Rows(rows: List<LimitAccount>, home: Boolean) {
        val palette = Palette.from(InterfaceTheme.Default)
        MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Column(Modifier.background(palette.shell).padding(16.dp)) {
                    if (home) HomeLimits(rows, DisplayOptions()) else rows.forEach { LimitAccountRow(it, DisplayOptions()) }
                }
            }
        }
    }

    @Test fun kiroRestoredQuotaAndZeroAppearOnHomeAndLimits() {
        var rows by mutableStateOf(accounts("kiro-v2"))
        var home by mutableStateOf(false)
        compose.setContent { Rows(rows, home) }
        compose.onNodeWithText("Kiro").assertIsDisplayed()
        compose.onNodeWithText("98% left").assertIsDisplayed()
        capture("v0841-kiro-limits")
        compose.runOnIdle { home = true }
        compose.onNodeWithText("98% left", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { rows = accounts("kiro-zero") }
        compose.onNodeWithText("0% left", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun copilotOmitsAbsentResetOnLimitsAndHomeAndKeepsFiniteReset() {
        var rows by mutableStateOf(accounts("copilot-mixed"))
        var home by mutableStateOf(false)
        compose.setContent { Rows(rows, home) }
        compose.onNodeWithText("75% left").assertIsDisplayed()
        compose.onNodeWithText("100% left").assertIsDisplayed()
        compose.onAllNodesWithText("Reset", substring = true).assertCountEquals(1)
        capture("v0841-copilot-mixed")
        compose.runOnIdle { rows = accounts("copilot-no-reset") }
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onAllNodesWithText("Reset", substring = true).assertCountEquals(0)
        compose.runOnIdle { home = true }
        compose.onNodeWithText("100% left", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Reset", substring = true, useUnmergedTree = true).assertCountEquals(0)
        capture("v0841-copilot-home")
    }
}
