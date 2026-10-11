package io.github.theminionooo.tokenmonitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import io.github.theminionooo.tokenmonitor.R
import kotlin.concurrent.thread

class SwipeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        updateAsync(context, intArrayOf(id))

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in PAGE_ACTIONS) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        WidgetDeckPageState.move(context, widgetId, if (intent.action == ACTION_PREVIOUS) -1 else 1)
        updateAsync(context, intArrayOf(widgetId))
    }

    override fun onDeleted(context: Context, ids: IntArray) = WidgetDeckPageState.remove(context, ids)

    override fun onDisabled(context: Context) = WidgetUpdateCoordinator.stopLiveIfUnused(context)

    /** Reading the cache, parsing and drawing the card are too slow for the broadcast thread. */
    private fun updateAsync(context: Context, ids: IntArray) {
        // Null when the provider is called directly rather than from a delivered broadcast.
        val result: PendingResult? = goAsync()
        val app = context.applicationContext
        thread(name = "pages-widget") {
            try { render(app, AppWidgetManager.getInstance(app), ids) } finally { result?.finish() }
        }
    }

    companion object {
        internal const val ACTION_PREVIOUS = "widget.DECK_PREVIOUS"
        internal const val ACTION_NEXT = "widget.DECK_NEXT"
        private val PAGE_ACTIONS = setOf(ACTION_PREVIOUS, ACTION_NEXT)

        internal fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, manager.getAppWidgetIds(ComponentName(context, SwipeWidgetProvider::class.java)))
        }

        /**
         * One render at a time, so an older snapshot is never posted after a newer one. A launcher that
         * refuses an update must not take the app down with it.
         */
        @Synchronized private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id -> runCatching { configure(context, manager, id) } }
        }

        private fun configure(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val state = loadWidgetDeck(context, widgetId)
            val page = WidgetDeckPageState.read(context, widgetId)
            fun sized(size: SizeF) = WidgetDeckRenderer.render(context, page, state.data, state.theme, size).apply {
                setOnClickPendingIntent(R.id.swipe_page_open, activityIntent(context, widgetId, 0, WidgetControlActivity.ACTION_OPEN))
                setOnClickPendingIntent(R.id.swipe_page_previous, pageIntent(context, widgetId, 1, ACTION_PREVIOUS))
                setOnClickPendingIntent(R.id.swipe_page_next, pageIntent(context, widgetId, 2, ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_refresh, activityIntent(context, widgetId, 3, WidgetLiveService.ACTION_REFRESH))
                setOnClickPendingIntent(R.id.widget_live, activityIntent(context, widgetId, 4, WidgetLiveService.ACTION_LIVE))
            }
            // The root is padded to the fitted card, so each size the launcher may show gets its own frame;
            // otherwise a rotated or resized allocation would misplace the touch targets.
            val options = manager.getAppWidgetOptions(widgetId)
            val views = if (Build.VERSION.SDK_INT >= 31) {
                val sizes = if (Build.VERSION.SDK_INT >= 33) {
                    options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                }.orEmpty().filter { it.width > 0 && it.height > 0 }.map(::deckAllocation).distinct().take(MAX_SIZES)
                if (sizes.isEmpty()) sized(state.size) else RemoteViews(sizes.associateWith(::sized))
            } else {
                fun allocation(widthKey: String, heightKey: String) =
                    deckAllocation(SizeF(options.getInt(widthKey).toFloat(), options.getInt(heightKey).toFloat()))
                val landscape = allocation(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                val portrait = allocation(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) <= 0) sized(state.size)
                else RemoteViews(sized(landscape), sized(portrait))
            }
            manager.updateAppWidget(widgetId, views)
        }

        /** Each bitmap is up to about 3 MB; a launcher lists one size per orientation or posture. */
        private const val MAX_SIZES = 4

        private fun activityIntent(context: Context, widgetId: Int, offset: Int, action: String): PendingIntent {
            val intent = Intent(context, WidgetControlActivity::class.java).apply {
                this.action = action
                data = Uri.parse("token-monitor://widget/deck/$widgetId/action/$offset")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun pageIntent(context: Context, widgetId: Int, offset: Int, action: String): PendingIntent {
            val intent = Intent(context, SwipeWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("token-monitor://widget/deck/$widgetId/page/$offset")
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
