package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production screens, synthetic state only; never reads a pairing or contacts a Hub. */
class InternetHubUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var savedConsent: Boolean? = null

    private fun screen(state: HubRepositoryState) {
        check(context.packageName.endsWith(".preview"))
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Box(Modifier.size(393.dp, 820.dp).background(palette.shell).testTag("https-screen")) {
                        DashboardScaffold(state = state, destination = DashboardDestination.Settings,
                            form = ConnectionFormState(), displayOptions = DisplayOptions(), serviceStatus = ServiceStatusState(),
                            onChoose = {}, onRefresh = {}, onSaveConnection = { _, _, _, _, internet -> savedConsent = internet },
                            onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = {},
                            onThemeCodeChange = {}, onFollowSystemThemeChange = {}, onShowLiveIndicatorChange = {}, onShowToolIconsChange = {}, onRankingMetricChange = {},
                            onShowLimitSourceChange = {}, onShowAccountEmailsChange = {}, onLimitBarMetricChange = {}, onDefaultPeriodChange = {},
                            onViewVisibleChange = { _, _ -> }, onHomeModuleVisibleChange = { _, _ -> }, onMoveView = { _, _ -> }, onMoveHomeModule = { _, _ -> },
                            onDisconnect = {}, onOpenServicePage = {}, onOpenReleasePage = {}, discovery = HubDiscoveryState(), onFindHomeHub = {})
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("https-screen").captureToImage().asAndroidBitmap()
        context.openFileOutput("https-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun pairingStartsOffAndExplainsTheSecretWhenEnabled() {
        screen(HubRepositoryState())
        capture("pairing-intro")
        val toggle = compose.onNodeWithContentDescription("Allow an internet Hub over HTTPS")
        toggle.performScrollTo().assertIsOff()
        compose.onNodeWithText("CONNECT", useUnmergedTree = true).performScrollTo()
        capture("pairing-off")
        toggle.performClick().assertIsOn()
        compose.onNodeWithText("The Hub secret can also change data on the Hub.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CONNECT", useUnmergedTree = true).performScrollTo()
        capture("pairing-on")
        compose.onNodeWithText("CONNECT", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(true, savedConsent)
    }

    @Test fun savedInternetConsentAppearsInConnectionSettings() {
        screen(HubRepositoryState(hasConnection = true, connectionUrl = "https://hub.example.com", allowPublicHttps = true))
        compose.onNodeWithText("CONNECTION", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Allow an internet Hub over HTTPS").performScrollTo().assertIsOn()
        compose.onNodeWithText("The Hub secret can also change data on the Hub.", substring = true).performScrollTo()
        compose.onNodeWithText("CHECK AND SAVE CONNECTION", useUnmergedTree = true).performScrollTo()
        capture("settings-on")
    }
}
