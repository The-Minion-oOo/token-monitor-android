package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.Rule
import org.junit.Test

class DesktopV078InteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun zcodeChildrenExpandAndSharedTitlesCanBeHiddenOrRevoked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun sessions(name: String) = HubProtocolParser.decodeStats(instrumentation.context.assets
            .open("protocol/v0.78.0/$name.json").bufferedReader().use { it.readText() }).periods.getValue("today").sessions
        var records by mutableStateOf(sessions("zcode-titles"))
        var showTitles by mutableStateOf(true)
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false,
                    LocalSessionTitles provides showTitles) {
                    LazyColumn(Modifier.background(palette.shell).padding(16.dp)) { sessionItems(records, DashboardPeriod.Today) }
                }
            }
        }
        compose.onNodeWithText("Plan the sample release").assertIsDisplayed()
        compose.onNodeWithText("Sample release alternative").assertIsDisplayed()
        compose.onNodeWithText("Sample release side chat").assertIsDisplayed()
        compose.onNodeWithText("Inspect the sample tests").assertDoesNotExist()
        compose.onNodeWithText("Plan the sample release").performClick()
        compose.onNodeWithText("Inspect the sample tests").assertIsDisplayed()
        compose.onNodeWithText("Check the sample docs").assertIsDisplayed()
        val capture = File(instrumentation.targetContext.filesDir, "v078-zcode-expanded.png")
        capture.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { showTitles = false }
        compose.onAllNodesWithText("Plan the sample release").assertCountEquals(0)
        compose.onNodeWithText("Inspect the sample tests").assertDoesNotExist()
        compose.runOnIdle { records = sessions("zcode-revoked"); showTitles = true }
        compose.onAllNodesWithText("Plan the sample release").assertCountEquals(0)
        compose.onNodeWithText("Sample release alternative").assertDoesNotExist()
    }
}
