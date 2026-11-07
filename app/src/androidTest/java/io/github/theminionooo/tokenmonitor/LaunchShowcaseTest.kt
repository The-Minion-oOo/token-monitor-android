package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.ui.*
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Captures production components with synthetic fixtures; never reads stored pairing or a network. */
class LaunchShowcaseTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val captured = Instant.parse("2026-09-07T18:00:00Z").toEpochMilli()
    private fun snapshot(): io.github.theminionooo.tokenmonitor.domain.HubSnapshot {
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        return HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), captured, false)
    }
    private fun save(name: String, bitmap: Bitmap) {
        context.openFileOutput("launch-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun homeGallery() = dashboardGallery(DashboardDestination.Home)
    @Test fun modelsGallery() = dashboardGallery(DashboardDestination.Models)
    @Test fun devicesGallery() = dashboardGallery(DashboardDestination.Devices)
    @Test fun projectsGallery() = dashboardGallery(DashboardDestination.Projects)
    @Test fun trendsGallery() = dashboardGallery(DashboardDestination.Trends)
    @Test fun settingsGallery() = dashboardGallery(DashboardDestination.Settings)
    @Test fun filteredModelsGallery() = dashboardGallery(DashboardDestination.Tools)

    @Test fun desktopNamesAndReconnectGallery() = dashboardGallery(DashboardDestination.Models, compareNames = true)

    private fun dashboardGallery(initial: DashboardDestination, compareNames: Boolean = false) {
        check(context.packageName.endsWith(".preview"))
        val source = snapshot().let { sample ->
            if (!compareNames) sample else sample.copy(stats = sample.stats.copy(modelAliasRevision = 1,
                periods = sample.stats.periods.mapValues { (_, period) -> period.copy(
                    sessions = period.sessions.map { it.copy(title = "", reasoningEffort = "high") },
                    projects = period.projects.map { it.copy(models = mapOf("gpt-5.6-sol" to it.totalTokens / 2,
                        "gpt-5.6-terra" to it.totalTokens - it.totalTokens / 2)) }) }))
        }
        var state by mutableStateOf(HubRepositoryState(hasConnection = true, snapshot = source, streamActive = true))
        var destination by mutableStateOf(initial)
        val palette = Palette.from(InterfaceTheme.Default)
        var captureWindow: android.view.Window? = null
        compose.setContent {
            var owner = androidx.compose.ui.platform.LocalView.current.context
            while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
            captureWindow = (owner as android.app.Activity).window
            MaterialTheme(colorScheme = tokenMonitorColors(palette), typography = tokenMonitorTypography(1)) {
                CompositionLocalProvider(LocalPalette provides palette, LocalInteractionMotion provides false, LocalNow provides captured, LocalColorfulToolMarks provides true) {
                    Box(Modifier.size(393.dp, 820.dp).background(Brush.linearGradient(colorStops = arrayOf(0f to palette.gradientTop, 0.38f to palette.shell, 1f to palette.gradientBottom))).testTag("showcase")) {
                        DashboardScaffold(state = state, destination = destination, form = ConnectionFormState(), displayOptions = DisplayOptions(colorfulToolMarks = true), serviceStatus = ServiceStatusState(),
                    onChoose = { destination = it }, onRefresh = {}, onSaveConnection = { _, _, _, _, _ -> },
                    onColorfulToolMarksChange = {}, onCompactTokenTotalChange = {}, onReduceMotionChange = {}, onTextScaleChange = {},
                    onThemeCodeChange = {}, onFollowSystemThemeChange = {}, onShowLiveIndicatorChange = {}, onShowToolIconsChange = {}, onRankingMetricChange = {},
                    onShowLimitSourceChange = {}, onShowAccountEmailsChange = {}, onLimitBarMetricChange = {}, onDefaultPeriodChange = {},
                    onViewVisibleChange = { _, _ -> }, onHomeModuleVisibleChange = { _, _ -> }, onMoveView = { _, _ -> }, onMoveHomeModule = { _, _ -> },
                    onDisconnect = {}, onOpenServicePage = {}, onOpenReleasePage = {}, discovery = HubDiscoveryState(), onFindHomeHub = {},
                        )
                    }
                }
            }
        }
        fun capture(name: String) {
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            val drawn = java.util.concurrent.CountDownLatch(1)
            val decor = checkNotNull(captureWindow).decorView
            decor.post {
                decor.requestLayout()
                decor.invalidate()
                decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
            }
            check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS))
            Thread.sleep(250)
            // Capture the final window composition, including positioned hardware layers.
            val bounds = compose.onNodeWithTag("showcase").fetchSemanticsNode().boundsInWindow
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            val copied = java.util.concurrent.CountDownLatch(1)
            var result = -1
            android.view.PixelCopy.request(checkNotNull(captureWindow), android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()), bitmap,
                { result = it; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            check(copied.await(5, java.util.concurrent.TimeUnit.SECONDS) && result == android.view.PixelCopy.SUCCESS)
            save(name, bitmap)
            bitmap.recycle()
        }
        if (compareNames) {
            val aliases = io.github.theminionooo.tokenmonitor.domain.ModelAliasSettings(1,
                mapOf("gpt-5-6-sol" to "GPT 5.6", "gpt-5-6-terra" to "GPT 5.6"),
                io.github.theminionooo.tokenmonitor.domain.ModelAliasGrouping.Duplicates)
            capture("names-models-before")
            compose.runOnIdle { state = state.copy(modelAliases = aliases) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("GPT 5.6").fetchSemanticsNodes().isNotEmpty() }
            capture("names-models-after")
            compose.onNodeWithText("GPT 5.6").assertIsDisplayed()
            compose.runOnIdle { destination = DashboardDestination.Sessions; state = state.copy(modelAliases = null) }
            capture("names-sessions-before")
            compose.runOnIdle { state = state.copy(modelAliases = aliases) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("GPT 5.6", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("names-sessions-after")
            fun compareView(view: DashboardDestination, label: String, prepare: () -> Unit = {}) {
                compose.runOnIdle { destination = view; state = state.copy(modelAliases = null) }
                compose.waitForIdle()
                prepare()
                capture("names-$label-before")
                compose.runOnIdle { state = state.copy(modelAliases = aliases) }
                capture("names-$label-after")
                if (view == DashboardDestination.Trends) {
                    compose.onNodeWithText("gpt-5.6-sol").assertDoesNotExist()
                    compose.onNodeWithText("GPT 5.6").assertExists()
                }
            }
            compareView(DashboardDestination.Sessions, "members") {
                compose.onAllNodesWithContentDescription("Expand session details").onFirst().performClick()
            }
            compareView(DashboardDestination.Home, "home")
            compareView(DashboardDestination.Devices, "device") {
                compose.onNodeWithText("Studio Workstation").performClick()
                compose.onNodeWithText("TOP MODELS ON THIS DEVICE").performScrollTo()
            }
            compareView(DashboardDestination.Projects, "project") {
                compose.onNodeWithText("Mobile Companion").performClick()
                compose.onNodeWithText("MODELS").performClick()
            }
            compareView(DashboardDestination.Trends, "history") {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("BY MODEL · AVAILABLE HISTORY").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("BY MODEL · AVAILABLE HISTORY").performScrollTo()
                compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            }
            compose.runOnIdle { destination = DashboardDestination.Home; state = state.copy(streamActive = false, message = "Live updates paused. Retrying...") }
            capture("reconnect-before")
            compose.runOnIdle { state = state.copy(message = null) }
            capture("reconnect-quiet")
            compose.runOnIdle { state = state.copy(message = context.getString(R.string.ui_hub_reconnecting)) }
            capture("reconnect-notice")
            instrumentation.runOnMainSync {
                for ((label, names) in listOf("before" to io.github.theminionooo.tokenmonitor.domain.ModelNames(), "after" to aliases.resolver(source))) {
                    val data = prepareWidgetDeck(source, WidgetSession(), captured, modelNames = names)
                    val view = WidgetDeckRenderer.render(context, WidgetDeckPage.Breakdown, data, InterfaceTheme.Default, android.util.SizeF(360f, 220f))
                        .apply(context, FrameLayout(context))
                    val width = (360 * context.resources.displayMetrics.density).toInt()
                    val height = (220 * context.resources.displayMetrics.density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    drawWidget(view, bitmap)
                    save("names-widget-$label", bitmap)
                    bitmap.recycle()
                }
            }
        } else if (initial == DashboardDestination.Tools) {
            compose.onNodeWithText("Codex").performClick()
            compose.onNodeWithText("Codex · Models").assertIsDisplayed()
            capture("filtered-models")
        } else {
            capture(initial.name.lowercase())
            if (initial == DashboardDestination.Trends) {
                compose.onNodeWithText("TRENDS").performClick()
                capture("trends-bars")
                compose.onNodeWithText("K-LINE").performClick()
                capture("trends-kline")
            }
        }
    }

    @Test fun widgetGallery() {
        val data = snapshot()
        instrumentation.runOnMainSync {
            val preview = android.widget.RemoteViews(context.packageName, R.layout.usage_widget_preview).apply(context, FrameLayout(context))
            val pixels = (160 * context.resources.displayMetrics.density).toInt()
            preview.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY))
            preview.layout(0, 0, pixels, pixels)
            val pickerBitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(pickerBitmap))
            save("widget-picker-preview", pickerBitmap)
            val sizes = listOf(Triple(WidgetLayout.Compact, 160, 140), Triple(WidgetLayout.Portrait, 160, 260), Triple(WidgetLayout.Wide, 360, 144), Triple(WidgetLayout.Overview, 360, 280), Triple(WidgetLayout.Large, 360, 400))
            for ((layout, widthDp, heightDp) in sizes) {
                val view = UsageWidgetProvider.render(context, data, InterfaceTheme.Default, layout, session = WidgetSession(enabled = true, connected = true, expiresAt = System.currentTimeMillis() + 3_600_000), size = android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())).apply(context, FrameLayout(context))
                val density = context.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawWidget(view, bitmap)
                save("widget-${layout.name.lowercase()}", bitmap)
            }
        }
    }
}
