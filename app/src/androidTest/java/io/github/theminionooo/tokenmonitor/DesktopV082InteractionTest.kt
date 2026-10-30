package io.github.theminionooo.tokenmonitor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopV082InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun stats(name: String) = HubProtocolParser.decodeStats(InstrumentationRegistry.getInstrumentation().context.assets
        .open("protocol/v0.82.0/$name.json").bufferedReader().use { it.readText() })
    @Before fun englishCopy() { AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags("en"))
    })) }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) { File(context.filesDir, "$name.png").outputStream().use {
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    } }
    @Composable private fun Surface(content: @Composable () -> Unit) {
        val palette = Palette.from(InterfaceTheme.Default)
        MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Column(Modifier.background(palette.shell).padding(16.dp)) { content() }
            }
        }
    }

    @Test fun homeAndSessionsSuppressOnlyCodexExpiryWhileKeepingBothCacheHitReadings() {
        val rows = stats("legacy-cache").periods.getValue("today").sessions
        val now = Instant.parse(rows.first { it.client == "claude" }.promptCache!!.observedAt).toEpochMilli() + 60_000
        var home by mutableStateOf(true)
        compose.setContent { Surface { CompositionLocalProvider(LocalNow provides now) {
            if (home) HomeSessionsModule(HomeSessionInputs(emptyList(), rows)) {}
            else LazyColumn { sessionItems(rows, DashboardPeriod.Today) }
        } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Cache hit 60%", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithText("Cache estimate", substring = true, useUnmergedTree = true).assertCountEquals(1)
        capture("v082-cache-home")
        compose.runOnIdle { home = false }
        compose.onAllNodesWithText("Cache hit 60%", substring = true, useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Cache estimate", substring = true, useUnmergedTree = true).assertCountEquals(1)
        capture("v082-cache-sessions")
    }

    @Test fun archivedModelBreakdownShowsObservedComponentsAndKeepsMissingEvidenceUnknown() {
        var observed by mutableStateOf(true)
        val complete = stats("archive-session-observed").periods.getValue("month")
        val legacy = stats("archive-session-legacy").periods.getValue("month")
        compose.setContent { Surface {
            val period = if (observed) complete else legacy
            TokenComponentBreakdown(period.models.getValue("alpha"), period.modelCacheReads["alpha"] ?: 0,
                period.modelOutputs["alpha"] ?: 0, period.modelUnclassifiedTokens["alpha"] ?: 0)
        } }
        compose.onNodeWithText("Unclassified").assertDoesNotExist()
        compose.onNodeWithText("20").assertIsDisplayed()
        capture("v082-archive-observed")
        compose.runOnIdle { observed = false }
        compose.onNodeWithText("Unclassified").assertIsDisplayed()
        compose.onNodeWithText("60").assertIsDisplayed()
    }
}
