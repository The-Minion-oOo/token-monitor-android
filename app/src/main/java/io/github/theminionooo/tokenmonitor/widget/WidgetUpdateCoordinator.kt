package io.github.theminionooo.tokenmonitor.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

internal object WidgetUpdateCoordinator {
    /** Called from the disk queue, Live and plain threads; a launcher that refuses an update must not crash any of them. */
    fun refresh(context: Context) {
        runCatching { UsageWidgetProvider.refresh(context) }
        runCatching { SwipeWidgetProvider.refresh(context) }
    }

    fun stopLiveIfUnused(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val hasResponsive = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java)).isNotEmpty()
        val hasDeck = manager.getAppWidgetIds(ComponentName(context, SwipeWidgetProvider::class.java)).isNotEmpty()
        if (!hasResponsive && !hasDeck) WidgetLiveService.stop(context)
    }
}
