package io.github.theminionooo.tokenmonitor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopV081InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun englishCopy() { AppLanguage.bind(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags("en"))
    })) }
    @After fun restoreCopy() { AppLanguage.bind(context) }
    private fun capture(name: String) { File(context.filesDir, "$name.png").outputStream().use {
        compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    } }

    @Test fun reasoningEffortRemainsVisibleWithSavedTitlesAndInactiveSessions() {
        val session = HubProtocolParser.decodeStats("""{"periods":{"today":{"sessions":{"sample":{"client":"codex","title":"Synthetic release review","models":{"gpt-5":1200},"totalTokens":1200,"reasoningEffort":"xhigh","archived":true}}}}}""")
            .periods.getValue("today").sessions.single()
        compose.setContent {
            val palette = Palette.from(InterfaceTheme.Default)
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Column(Modifier.background(palette.shell).padding(16.dp)) {
                        val labels = sessionRowLabels(session)
                        SessionUsageRow(session, labels.title, labels.meta, 1f)
                    }
                }
            }
        }
        compose.onNodeWithText("Synthetic release review").assertIsDisplayed()
        compose.onNodeWithText("gpt-5 · xhigh", substring = true).assertIsDisplayed()
        capture("v081-session-effort")
    }

    @Test fun personalVolcenginePlansShowSeparateWindowsIncludingZero() {
        val raw = InstrumentationRegistry.getInstrumentation().context.assets.open("protocol/v0.81.0/volc-personal.json").bufferedReader().use { it.readText() }
        val accounts = HubProtocolParser.decodeStats(raw).limits.providers
        compose.setContent {
            val palette = Palette.from(InterfaceTheme.Default)
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                    Column(Modifier.background(palette.shell).padding(16.dp)) {
                        accounts.forEach { LimitAccountRow(it, DisplayOptions()) }
                    }
                }
            }
        }
        compose.onAllNodesWithText("Volcengine").assertCountEquals(2)
        compose.onNodeWithText("0% left").assertIsDisplayed()
        compose.onNodeWithText("88% left").assertIsDisplayed()
        capture("v081-volc-personal")
    }
}
