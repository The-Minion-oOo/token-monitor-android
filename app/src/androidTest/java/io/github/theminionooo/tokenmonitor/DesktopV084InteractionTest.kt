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

class DesktopV084InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun accounts(name: String) = HubProtocolParser.decodeStats(InstrumentationRegistry.getInstrumentation().context.assets
        .open("protocol/v0.84.0/$name.json").bufferedReader().use { it.readText() }).limits.providers
    @Before fun englishCopy() { AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags("en"))
    })) }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) { File(context.filesDir, "$name.png").outputStream().use {
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    } }
    @Composable private fun Rows(rows: List<LimitAccount>, home: Boolean = false) {
        val palette = Palette.from(InterfaceTheme.Default)
        MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Column(Modifier.background(palette.shell).padding(16.dp)) {
                    if (home) HomeLimits(rows, DisplayOptions()) else rows.forEach { LimitAccountRow(it, DisplayOptions()) }
                }
            }
        }
    }

    @Test fun manusAllowancesAndZeroCreditsHaveTheRightMeters() {
        var rows by mutableStateOf(accounts("manus-credits"))
        compose.setContent { Rows(rows) }
        compose.onNodeWithText("Manus").assertIsDisplayed()
        compose.onNodeWithText("60% left").assertIsDisplayed()
        compose.onNodeWithText("75% left").assertIsDisplayed()
        compose.onNodeWithText("1,180.00").assertIsDisplayed()
        capture("v084-manus-credits")
        compose.runOnIdle { rows = accounts("manus-zero") }
        compose.onNodeWithText("0.00").assertIsDisplayed()
        compose.onNodeWithText("0% left").assertDoesNotExist()
    }

    @Test fun museProductsAndAdditionalTokensAppearOnLimitsAndHome() {
        var home by mutableStateOf(false)
        val rows = accounts("muse-products")
        compose.setContent { Rows(rows, home) }
        compose.onAllNodesWithText("Muse Code").assertCountEquals(2)
        compose.onNodeWithText("Muse").assertIsDisplayed()
        compose.onNodeWithText("Additional tokens").assertIsDisplayed()
        compose.onNodeWithText("1.5B").assertIsDisplayed()
        compose.onNodeWithText("$60.00").assertDoesNotExist()
        capture("v084-muse-products")
        compose.runOnIdle { home = true }
        compose.onNodeWithText("Muse", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Additional tokens", useUnmergedTree = true).assertIsDisplayed()
        capture("v084-muse-home")
    }

    @Test fun missingAndExpiredProvidersDoNotShowExhaustedQuota() {
        compose.setContent { Rows(accounts("manus-missing") + accounts("muse-web-expired")) }
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Sign in again").assertIsDisplayed()
        compose.onNodeWithText("0% left").assertDoesNotExist()
    }
}
