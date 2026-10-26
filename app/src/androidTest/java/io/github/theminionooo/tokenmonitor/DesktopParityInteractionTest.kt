package io.github.theminionooo.tokenmonitor

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.domain.*
import io.github.theminionooo.tokenmonitor.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopParityInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @After fun restoreLanguage() { AppLanguage.bind(context) }

    private fun content(block: @Composable () -> Unit) {
        val palette = Palette.from(InterfaceTheme.Default)
        compose.setContent { MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
            CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false) {
                Box(Modifier.fillMaxSize().background(palette.shell).padding(14.dp)) { block() }
            }
        } }
    }

    @Test fun topUpPaymentsExpandWithoutBecomingMonthlySubscriptionsOrRevealingEmail() {
        val record = Subscription("payment", "openai", "API credits", 0, "USD", "2026-01-31", "month", false,
            kind = "topup", binding = SubscriptionBinding("Synthetic profile", accountEmail = "private@example.com"),
            topUps = listOf(SubscriptionTopUp("one", "2026-10-01", 1200)), note = "Synthetic payment note")
        content { Column { SubscriptionRow(record, false) } }
        compose.onNodeWithText("Total paid").assertIsDisplayed()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
        compose.onNodeWithText("private@example.com", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Show payment details").performClick()
        compose.onNodeWithText("Synthetic payment note").assertIsDisplayed()
        compose.onNodeWithText("2026-10-01", substring = true).assertIsDisplayed()
    }

    @Test fun resetCreditsAndTokenSummaryExpandWithoutChangingQuotaWindows() {
        val account = HubProtocolParser.decodeStats("""{"limits":{"providers":[{"provider":"claude","resetCredits":{"availableCount":0,"grants":[{"label":"Synthetic grant","resetsLeft":0,"resetsTotal":2,"usableNow":false,"useRequiresLimit":true}]},"usageSummary":{"todayTokens":1200,"requests":4}}]}}""").limits.providers.single()
        content { ProviderDetailRows(account) }
        compose.onNodeWithText("Reset credits: 0").assertIsDisplayed()
        compose.onNodeWithText("Show quota details").performClick()
        compose.onNodeWithText("Not usable now").assertIsDisplayed()
        compose.onNodeWithText("Requires a reached limit").assertIsDisplayed()
        compose.onNodeWithText("Requests: 4").assertIsDisplayed()
        assertTrue(account.windows.isEmpty())
    }

    @Test fun pinAndRestoreAreLocalDisplayChoices() {
        var options by mutableStateOf(DisplayOptions())
        val snapshot = HubSnapshot(stats = HubStats(periods = mapOf("today" to UsagePeriod(clients = mapOf("codex" to 100)))))
        content { LazyColumn { item { DisplayItemSettings(snapshot, options) { options = it } } } }
        compose.onNodeWithText("TOOLS AND QUOTAS").performClick()
        compose.onNodeWithText("Pin").performClick()
        compose.runOnIdle { assertEquals(listOf("codex"), options.pinnedTools); assertEquals(100L, snapshot.today.clients["codex"]) }
        compose.onNodeWithText("Restore display defaults").performClick()
        compose.runOnIdle { assertTrue(options.pinnedTools.isEmpty()) }
    }

    @Test fun manualCurrencyChangesUsageCostsWhilePaymentsKeepTheirCurrency() {
        var options by mutableStateOf(DisplayOptions(currencyCode = "INR", manualCurrencyRate = true))
        content { LazyColumn { item { CurrencySettings(options) { options = it } }; item {
            Text(formatUsageCost(2.0, currency = usageCurrency(options)))
            Text(formatSubscriptionAmount(200, "USD"))
        } } }
        compose.onNodeWithText("DISPLAY CURRENCY").performClick()
        compose.onNodeWithText("Currency units per USD").performTextInput("80")
        compose.onNodeWithText("Save exchange rate").performClick()
        compose.runOnIdle { assertEquals(80.0, options.manualRates["INR"]!!, 0.0) }
        compose.onNodeWithText(formatUsageCost(2.0, currency = UsageCurrency("INR", 80.0))).assertIsDisplayed()
        compose.onNodeWithText("$2.00").assertIsDisplayed()
    }

    @Test fun diagnosticsRequirePreviewAndDoNotIncludeRawConnectionText() {
        content { DiagnosticActions(HubRepositoryState(connectionUrl = "https://private-marker.example.com", message = "SECRET_MARKER"), DisplayOptions()) }
        compose.onNodeWithText("Copy report").assertDoesNotExist()
        compose.onNodeWithText("Preview diagnostic report").performClick()
        compose.onNodeWithText("private-marker", substring = true).assertDoesNotExist()
        compose.onNodeWithText("SECRET_MARKER", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Copy report").performClick()
        compose.onNodeWithText("Copied").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Share report").assertDoesNotExist()
    }

    @Test fun liveRateShowsMatchedOutputRateWithoutAPercentageSuffix() {
        var snapshot by mutableStateOf(HubSnapshot(stats = HubStats(periods = mapOf("today" to UsagePeriod(throughputAvailable = true, timedTokens = 100, timedOutputTokens = 50, timedDurationMs = 1000))), capturedAt = 1))
        content { LiveRatePanel(snapshot, DisplayOptions(liveRateMode = LiveRateMode.Speed), true, "synthetic") }
        compose.onNodeWithText("Waiting for matched live samples").assertIsDisplayed()
        compose.runOnIdle { snapshot = snapshot.copy(stats = HubStats(periods = mapOf("today" to snapshot.today.copy(timedTokens = 300, timedOutputTokens = 100, timedDurationMs = 2000))), capturedAt = 2) }
        compose.onNodeWithText("50.0 tok/s").assertIsDisplayed()
        compose.onNodeWithText("50%", substring = true).assertDoesNotExist()
    }

    @Test fun everyLanguageRendersDashboardAndKeepsStableViewKeys() {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), 1, false)
        var tag by mutableStateOf("en")
        content { key(tag) { Column {
            Text(displayLabel("Home"), color = Ink)
            DashboardContent(Modifier.weight(1f), HubRepositoryState(snapshot = snapshot), DashboardDestination.Home,
                DashboardPeriod.Today, {}, {}, false, ServiceStatusState(), DisplayOptions(), {})
        } } }
        for (language in AppLanguage.choices.keys.filter(String::isNotBlank)) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(language)) })
            compose.runOnIdle { AppLanguage.bind(localized); tag = language }
            compose.onNodeWithText(localized.getString(R.string.label_home)).assertIsDisplayed()
            assertEquals("Home", DashboardDestination.Home.title)
            assertEquals("today", DashboardPeriod.Today.statsKey)
            context.openFileOutput("locale-$language.png", 0).use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val data = io.github.theminionooo.tokenmonitor.widget.prepareWidgetDeck(snapshot,
                io.github.theminionooo.tokenmonitor.widget.WidgetSession(), locale = java.util.Locale.forLanguageTag(language))
            for (page in io.github.theminionooo.tokenmonitor.widget.WidgetDeckPage.entries) {
                val bitmap = io.github.theminionooo.tokenmonitor.widget.WidgetDeckRenderer.renderBitmap(localized, page, data,
                    Palette.from(InterfaceTheme.Default), android.util.SizeF(364f, 200f))
                context.openFileOutput("locale-widget-$language-${page.name}.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun hiddenToolsAndQuotasStayHiddenInBothWidgetsAndTheLiveNotification() {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val snapshot = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), 1, false)
        val options = DisplayOptions(hiddenTools = setOf("codex"), hiddenProviders = setOf("codex"))
        val deck = io.github.theminionooo.tokenmonitor.widget.prepareWidgetDeck(snapshot,
            io.github.theminionooo.tokenmonitor.widget.WidgetSession(), displayOptions = options)
        assertFalse(deck.tools.any { it.name == "codex" })
        assertFalse(deck.limitGroups.any { it.provider == "codex" })
        val notice = io.github.theminionooo.tokenmonitor.widget.widgetNotificationContent(
            io.github.theminionooo.tokenmonitor.widget.WidgetSession(snapshot = snapshot), true, displayOptions = options)
        assertFalse(notice.detail.contains("Codex"))
        instrumentation.runOnMainSync {
            val views = io.github.theminionooo.tokenmonitor.widget.UsageWidgetProvider.render(context, snapshot,
                InterfaceTheme.Default, io.github.theminionooo.tokenmonitor.widget.WidgetLayout.Large, displayOptions = options)
            val root = views.apply(context, android.widget.FrameLayout(context))
            fun texts(view: android.view.View): List<String> = when (view) {
                is android.widget.TextView -> listOf(view.text.toString())
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            assertFalse(texts(root).any { it.contains("Codex") })
            assertEquals(formatTokens(snapshot.today.totalTokens), root.findViewById<android.widget.TextView>(R.id.widget_tokens).text.toString())
        }
    }
}
