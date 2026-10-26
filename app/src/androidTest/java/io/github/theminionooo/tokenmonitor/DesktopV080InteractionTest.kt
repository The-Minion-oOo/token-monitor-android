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
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.domain.LimitAccount
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopV080InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun accounts(name: String) = HubProtocolParser.decodeStats(InstrumentationRegistry.getInstrumentation().context.assets
        .open("protocol/v0.80.0/$name.json").bufferedReader().use { it.readText() }).limits.providers
    @Before fun englishCopy() {
        AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("en"))
        }))
    }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) {
        File(context.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Composable private fun Rows(rows: List<LimitAccount>, options: DisplayOptions = DisplayOptions(), home: Boolean = false) {
        val palette = Palette.from(InterfaceTheme.Default)
        MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Column(Modifier.background(palette.shell).padding(16.dp)) {
                    if (home) HomeLimits(rows, options) else rows.forEach { LimitAccountRow(it, options) }
                }
            }
        }
    }

    @Test fun museQuotasShowBothDevicesWithTheirPlanAndSeparateWindows() {
        val rows = accounts("muse-devices")
        compose.setContent { Rows(rows, DisplayOptions().copy(showLimitSource = true)) }
        compose.onAllNodesWithText("Muse Code").assertCountEquals(2)
        compose.onAllNodesWithText("75% left").assertCountEquals(2)
        compose.onAllNodesWithText("20% left").assertCountEquals(2)
        rows.forEach { row -> compose.onNodeWithText(row.sourceDeviceId.displayName(), substring = true).assertIsDisplayed() }
        capture("v080-muse-devices")
    }

    @Test fun ollamaCreditsAndMonthlySpendUseMoneyInBothBarModes() {
        var rows by mutableStateOf(accounts("ollama-credits"))
        var options by mutableStateOf(DisplayOptions())
        var home by mutableStateOf(false)
        compose.setContent { Rows(rows, options, home) }
        for (amount in listOf(15.0, 3.25, 5.0)) compose.onNodeWithText(formatWindowAmount(amount, "USD")).assertIsDisplayed()
        compose.onNodeWithText("Not reported").assertDoesNotExist()
        capture("v080-ollama-credits")
        compose.runOnIdle { options = options.copy(limitBarMetric = LimitBarMetric.Used) }
        compose.onNodeWithText(formatWindowAmount(15.0, "USD")).assertIsDisplayed()
        compose.onNodeWithText(formatWindowAmount(5.0, "USD")).assertIsDisplayed()
        compose.runOnIdle {
            val account = rows.single()
            rows = listOf(account.copy(windows = account.windows.filter { it.metric == "spend" }))
            home = true
        }
        compose.onNodeWithText(formatWindowAmount(5.0, "USD")).assertIsDisplayed()
        compose.onNodeWithText("No account limits available").assertDoesNotExist()
        for ((name, amount) in listOf("ollama-zero" to 0.0, "ollama-negative" to -1.0)) {
            compose.runOnIdle { home = false; rows = accounts(name) }
            compose.onNodeWithText(formatWindowAmount(amount, "USD")).assertIsDisplayed()
            compose.onNodeWithText("0% left").assertDoesNotExist()
            compose.onNodeWithText("100% left").assertDoesNotExist()
        }
    }

    @Test fun claudeManagedAndAutomaticAccountsRemainSeparateRows() {
        val rows = accounts("claude-accounts")
        compose.setContent { Rows(rows, DisplayOptions().copy(showAccountEmails = true)) }
        compose.onAllNodesWithText("Claude").assertCountEquals(3)
        rows.forEach { row -> compose.onNodeWithText(row.accountEmail, substring = true).assertIsDisplayed() }
        capture("v080-claude-accounts")
    }
}
