package io.github.theminionooo.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test

class DesktopV090InteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completeHistoryAndSeparateQuotaReadingsAppearOnDashboard() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fun read(name: String) = assets.open("protocol/v0.90.0/$name.json").bufferedReader().use { it.readText() }
        val snapshot = HubProtocolParser.decodeSnapshot(
            read("health"), read("v090-complete"), null, read("v090-complete-history"), null, 0,
        )
        val palette = Palette.from(InterfaceTheme.Default)
        var destination by mutableStateOf(DashboardDestination.Home)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    DashboardContent(
                        Modifier.fillMaxSize().background(palette.shell), HubRepositoryState(snapshot = snapshot),
                        destination, DashboardPeriod.Today, {}, {}, false, ServiceStatusState(), DisplayOptions(), {},
                    )
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Lifetime active days: 2"))
        compose.onNodeWithText("Lifetime active days: 2").assertIsDisplayed()

        compose.runOnIdle { destination = DashboardDestination.Limits }
        list.performScrollToNode(hasText("20.0K / 100.0K"))
        compose.onNodeWithText("20.0K / 100.0K").assertIsDisplayed()
        list.performScrollToNode(hasText("Team Credits"))
        compose.onNodeWithText("Credits").assertIsDisplayed()
        compose.onNodeWithText("Team Credits").assertIsDisplayed()
    }
}
