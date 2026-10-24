package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.Espresso.pressBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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

    private var disabledCalls = 0
    private var savedPairing by mutableStateOf(HubRepositoryState())
    private val dialogTitle = "Turn off internet access?"

    private fun screen(state: HubRepositoryState, theme: InterfaceTheme = InterfaceTheme.Default) {
        savedPairing = state
        check(context.packageName.endsWith(".preview"))
        val palette = Palette.from(theme)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Box(Modifier.size(393.dp, 820.dp).background(palette.shell).testTag("https-screen")) {
                        DashboardScaffold(state = savedPairing, destination = DashboardDestination.Settings,
                            form = ConnectionFormState(), displayOptions = DisplayOptions(), serviceStatus = ServiceStatusState(),
                            onChoose = {}, onRefresh = {}, onSaveConnection = { _, _, _, _, internet -> savedConsent = internet },
                            onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = {},
                            onThemeCodeChange = {}, onFollowSystemThemeChange = {}, onShowLiveIndicatorChange = {}, onShowToolIconsChange = {}, onRankingMetricChange = {},
                            onShowLimitSourceChange = {}, onShowAccountEmailsChange = {}, onLimitBarMetricChange = {}, onDefaultPeriodChange = {},
                            onViewVisibleChange = { _, _ -> }, onHomeModuleVisibleChange = { _, _ -> }, onMoveView = { _, _ -> }, onMoveHomeModule = { _, _ -> },
                            onDisablePublicHttps = {
                                disabledCalls++
                                savedPairing = savedPairing.copy(allowPublicHttps = false)
                            },
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
        toggle.performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        assertEquals(0, disabledCalls)
        toggle.performClick().assertIsOn()
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

    private fun internetPairing() = HubRepositoryState(
        hasConnection = true, connectionUrl = "https://hub.example.com", allowPublicHttps = true,
    )

    private fun toggle() = compose.onNodeWithContentDescription("Allow an internet Hub over HTTPS")

    private fun openConnection() {
        compose.onNodeWithText("CONNECTION", useUnmergedTree = true).performClick()
        toggle().performScrollTo().assertIsOn()
    }

    private fun captureDialog(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        context.openFileOutput("https-dialog-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertPairingUnchanged(initial: HubRepositoryState) {
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        toggle().assertIsOn()
        compose.runOnIdle {
            assertEquals(initial, savedPairing)
            assertEquals(0, disabledCalls)
        }
    }

    @Test fun savedInternetPairingRequiresConfirmationAndCancelIsTheDefault() {
        val initial = internetPairing()
        screen(initial)
        openConnection()
        // The saved route, rather than an unsaved edit, decides whether access is lost.
        compose.onNodeWithText(initial.connectionUrl!!).performScrollTo().performTextReplacement("100.88.1.2")
        toggle().performScrollTo().performClick()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNodeWithText("This phone will stop connecting to your internet Hub. To turn it back on, you'll need to enter the Hub secret again.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initial, savedPairing); assertEquals(0, disabledCalls) }
        captureDialog("dark")
        compose.onNodeWithText("Cancel").performClick()
        assertPairingUnchanged(initial)
        toggle().performClick()
        compose.onNodeWithText("Turn off").performClick()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        toggle().assertIsOff()
        compose.runOnIdle {
            assertEquals(1, disabledCalls)
            assertEquals(initial.copy(allowPublicHttps = false), savedPairing)
        }
    }

    @Test fun backDismissesTheLightDialogWithoutChangingPairing() {
        val initial = internetPairing()
        screen(initial, InterfaceTheme.Porcelain)
        openConnection()
        toggle().performClick()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        captureDialog("light")
        pressBack()
        assertPairingUnchanged(initial)
    }

    @Test fun outsideTapDismissesWithoutChangingPairing() {
        val initial = internetPairing()
        screen(initial)
        openConnection()
        toggle().performClick()
        compose.onNodeWithText(dialogTitle).assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 1f, context.resources.displayMetrics.heightPixels / 2f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        compose.waitForIdle()
        assertPairingUnchanged(initial)
    }

    private fun assertPrivatePairingTurnsOffSilently(url: String, local: Boolean = false) {
        val initial = HubRepositoryState(hasConnection = true, connectionUrl = url, allowPublicHttps = true, allowLocalNetwork = local)
        screen(initial)
        openConnection()
        toggle().performClick().assertIsOff()
        compose.onNodeWithText(dialogTitle).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, disabledCalls)
            assertEquals(initial.copy(allowPublicHttps = false), savedPairing)
        }
    }

    @Test fun tailscalePairingTurnsOffWithoutADialog() = assertPrivatePairingTurnsOffSilently("http://100.88.1.2:17321")

    @Test fun homeWifiPairingTurnsOffWithoutADialog() = assertPrivatePairingTurnsOffSilently("http://192.168.1.2:17321", local = true)

}
