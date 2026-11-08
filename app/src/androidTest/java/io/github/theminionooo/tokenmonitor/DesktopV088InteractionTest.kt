package io.github.theminionooo.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.Rule
import org.junit.Test

class DesktopV088InteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun miniMaxPointsAndApiMoneyHaveSeparateHeadings() {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets
            .open("protocol/v0.88.0/minimax-products.json").bufferedReader().use { it.readText() }
        val snapshot = HubSnapshot(stats = HubProtocolParser.decodeStats(raw))
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent {
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    DashboardContent(Modifier.fillMaxSize().background(palette.shell), HubRepositoryState(snapshot = snapshot),
                        DashboardDestination.Limits, DashboardPeriod.Today, {}, {}, false,
                        ServiceStatusState(), DisplayOptions(), {})
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("MiniMax API"))
        compose.onAllNodesWithText("MiniMax API").onFirst().assertIsDisplayed()
        list.performScrollToNode(hasText("MiniMax Code"))
        compose.onNodeWithText("MiniMax Code").assertIsDisplayed()
        compose.onNodeWithText("4,624.00").assertIsDisplayed()
        compose.onNodeWithText("Credits").assertIsDisplayed()
    }
}
