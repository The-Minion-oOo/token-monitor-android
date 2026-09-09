package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.widget.UsageWidgetProvider
import io.github.theminionooo.tokenmonitor.widget.WidgetLayout
import io.github.theminionooo.tokenmonitor.widget.WidgetSession
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Production RemoteViews at real launcher proportions; all data is synthetic. */
class WidgetDesignTest {
    @Test fun compositionsKeepReadableHierarchyAcrossSizesAndStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".preview"))
        fun asset(name: String) = instrumentation.context.assets.open("showcase/$name.json").bufferedReader().use { it.readText() }
        val parsed = HubProtocolParser.decodeSnapshot(asset("health"), asset("stats"), asset("devices"), asset("history"), asset("subscriptions"), Instant.parse("2026-09-07T18:00:00Z").toEpochMilli(), false)
        val snapshot = parsed.copy(stats = parsed.stats.copy(periods = parsed.stats.periods + ("today" to parsed.today.copy(totalTokens = 125_430_000, costUsd = 72.96))))
        val shapes = listOf(
            Triple(WidgetLayout.Compact, 110, 110), Triple(WidgetLayout.Compact, 160, 160),
            Triple(WidgetLayout.Wide, 360, 120), Triple(WidgetLayout.Portrait, 160, 260),
            Triple(WidgetLayout.Overview, 360, 200), Triple(WidgetLayout.Overview, 360, 280), Triple(WidgetLayout.Large, 360, 400),
            Triple(WidgetLayout.Large, 360, 500),
        )
        instrumentation.runOnMainSync {
            for ((layout, widthDp, heightDp) in shapes) {
                for (state in listOf("live", "saved", "empty", "connecting", "waiting")) {
                    val session = WidgetSession(enabled = state in listOf("live", "connecting", "waiting"), connected = state == "live", expiresAt = System.currentTimeMillis() + 3_600_000)
                    val view = UsageWidgetProvider.render(context, snapshot.takeUnless { state in listOf("empty", "waiting") }, InterfaceTheme.Default, layout, session = session, size = android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())).apply(context, FrameLayout(context))
                    val density = context.resources.displayMetrics.density
                    val width = (widthDp * density).toInt()
                    val height = (heightDp * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    if (state in listOf("empty", "waiting")) {
                        assertTrue(view.findViewById<TextView>(R.id.widget_refresh).text.toString().startsWith("OPEN"))
                        assertEquals(if (state == "waiting") View.VISIBLE else View.GONE, view.findViewById<View>(R.id.widget_live_container).visibility)
                    } else if (layout == WidgetLayout.Large) {
                        val bars = view.findViewById<android.widget.ImageView>(R.id.widget_chart_bars)
                        val drawn = (bars.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                        assertEquals("Chart bars are drawn at the height they are shown", bars.height, drawn.height)
                        assertEquals("Chart bars are drawn at the width they are shown", bars.width, drawn.width)
                        assertEquals("TOKEN MONITOR", view.findViewById<TextView>(R.id.widget_title).text.toString())
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    drawWidget(view, bitmap)
                    context.openFileOutput("design-${layout.name.lowercase()}-${widthDp}x${heightDp}-$state.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }
    @Test fun emptyStateKeepsOpenAndStopActionsAtMinimumSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f)) {
                val configuration = android.content.res.Configuration(context.resources.configuration).apply { fontScale = scale }
                val sized = context.createConfigurationContext(configuration)
                val density = sized.resources.displayMetrics.density
                for (layout in WidgetLayout.entries) {
                    val session = WidgetSession(enabled = true, expiresAt = System.currentTimeMillis() + 3_600_000)
                    val view = UsageWidgetProvider.render(sized, null, InterfaceTheme.Default, layout, session = session).apply(sized, FrameLayout(sized))
                    val width = (layout.width * density).toInt()
                    val height = (layout.height * density * if (layout in listOf(WidgetLayout.Compact, WidgetLayout.Wide)) 1f else scale).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    for (id in listOf(R.id.widget_refresh, R.id.widget_live)) {
                        val button = view.findViewById<TextView>(id)
                        val bounds = android.graphics.Rect()
                        button.getDrawingRect(bounds)
                        (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(button, bounds)
                        assertTrue("$layout at $scale: empty-state action must fit", bounds.bottom <= height - view.findViewById<View>(R.id.widget_root).paddingBottom && bounds.right <= width - view.findViewById<View>(R.id.widget_root).paddingRight)
                        assertTrue("$layout at $scale: empty-state touch target", button.width >= 48 * density - 1 && button.height >= 48 * density - 1)
                        assertTrue(button.paint.measureText(button.text.toString()) <= button.width)
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    drawWidget(view, bitmap)
                    context.openFileOutput("design-empty-minimum-${layout.name.lowercase()}-$scale.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }

}
