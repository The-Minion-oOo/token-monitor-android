package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetBackgroundUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun controls(theme: InterfaceTheme) {
        check(context.packageName.endsWith(".preview"))
        val palette = Palette.from(theme)
        compose.setContent {
            var settings by remember { mutableStateOf(WidgetRefreshSettings()) }
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette) {
                    Column(Modifier.width(393.dp).background(palette.shell).testTag("background-settings")
                        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WidgetBackgroundControls(settings, WidgetRefreshRecord()) { settings = it }
                    }
                }
            }
        }
    }

    @Test fun intervalAndWifiChoicesStartSafeAndCanBeChanged() {
        controls(InterfaceTheme.Porcelain)
        compose.onNodeWithText("Off").assertIsSelected()
        compose.onNodeWithText("Every 30 minutes").assertIsNotSelected()
        compose.onNodeWithContentDescription("Wi-Fi only").assertIsOn()
        capture("settings-light")
        compose.onNodeWithText("Every 30 minutes").performClick()
        compose.onNodeWithContentDescription("Wi-Fi only").performClick().assertIsOff()
        compose.onNodeWithText("Every 60 minutes").performClick()
        compose.onNodeWithText("Every 60 minutes").assertIsSelected()
        compose.onNodeWithText("Every 30 minutes").assertIsNotSelected()
        compose.onNodeWithText("Off").performClick()
    }

    @Test fun darkSettingsKeepTheSameOptions() {
        controls(InterfaceTheme.Default)
        compose.onNodeWithText("Every 60 minutes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wi-Fi only").assertIsOn()
        capture("settings-dark")
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("background-settings").captureToImage().asAndroidBitmap()
        context.openFileOutput("background-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
