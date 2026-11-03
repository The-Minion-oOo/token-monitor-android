package io.github.theminionooo.tokenmonitor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopV085InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot(name: String) = HubSnapshot(stats = HubProtocolParser.decodeStats(InstrumentationRegistry.getInstrumentation().context.assets
        .open("protocol/v0.85.0/$name.json").bufferedReader().use { it.readText() }))
    @Before fun englishCopy() { AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags("en"))
    })) }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) { File(context.filesDir, "$name.png").outputStream().use {
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    } }
    @Composable private fun Screen(data: HubSnapshot, showTitles: Boolean = true) {
        val palette = Palette.from(InterfaceTheme.Default)
        MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false, LocalSessionTitles provides showTitles) {
                DashboardContent(Modifier.fillMaxSize().background(palette.shell), HubRepositoryState(snapshot = data), DashboardDestination.Sessions,
                    DashboardPeriod.Today, {}, {}, false, ServiceStatusState(), DisplayOptions(), {})
            }
        }
    }

    @Test fun toolFilterKeepsWholeConversationsAndTogglesBackToAll() {
        val data = snapshot("session-tools")
        compose.setContent { Screen(data) }
        compose.onNodeWithText("All tools").assertIsSelected()
        compose.onNodeWithText("Claude Code").performClick().assertIsSelected()
        compose.onNodeWithText("Release plan").assertIsDisplayed()
        compose.onNodeWithText("Review changes").assertIsDisplayed()
        compose.onNodeWithText("Editor chat").assertDoesNotExist()
        compose.onNodeWithText("Release plan").performClick()
        compose.onNodeWithText("Write tests").assertIsDisplayed()
        capture("v085-session-filter")
        compose.onNodeWithText("Release plan").performClick()
        compose.onNodeWithText("Claude Code").performClick()
        compose.onNodeWithText("All tools").assertIsSelected()
        compose.onNodeWithText("Editor chat").assertIsDisplayed()
        org.junit.Assert.assertEquals(250L, data.today.totalTokens)
    }

    @Test fun filterCombinesWithSearchAndClearsWhenItsToolDisappears() {
        val original = snapshot("session-tools")
        var data by mutableStateOf(original)
        compose.setContent { Screen(data) }
        compose.onNodeWithText("Claude Code").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Review")
        compose.onNodeWithText("Review changes").assertIsDisplayed()
        compose.onNodeWithText("Release plan").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.runOnIdle { data = original.copy(stats = original.stats.copy(periods = original.stats.periods.mapValues { (_, value) ->
            value.copy(sessions = value.sessions.filter { it.client == "codex" })
        })) }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        compose.onNodeWithText("Release plan").assertIsDisplayed()
    }

    @Test fun hiddenGroupTitlesDoNotMatchSearch() {
        compose.setContent { Screen(snapshot("session-tools"), showTitles = false) }
        compose.onNodeWithText("Release plan").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Release plan")
        compose.onNodeWithText("No sessions match this search.").assertIsDisplayed()
    }

    @Test fun reportedHardwareIconsAppearOnDevicesAndHome() {
        val data = snapshot("hardware-kinds")
        var home by mutableStateOf(false)
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent { MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Column(Modifier.background(palette.shell).padding(16.dp)) {
                    if (home) HomeDevices(data.stats.devices, DashboardPeriod.Today, data.today, count = 8)
                    else data.stats.devices.forEach { device -> DeviceUsageRow(device, DashboardPeriod.Today.usage(device), "Sample OS", "Live uploads", 1f) }
                }
            }
        } }
        compose.onNodeWithText("Sample laptop").assertIsDisplayed()
        compose.onNodeWithText("Sample server").assertIsDisplayed()
        capture("v085-device-hardware")
        compose.runOnIdle { home = true }
        compose.onNodeWithText("Sample laptop").assertIsDisplayed()
        capture("v085-home-hardware")
    }
}
