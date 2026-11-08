package io.github.theminionooo.tokenmonitor.widget

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import io.github.theminionooo.tokenmonitor.MainActivity
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.ui.appText

internal data class WidgetLivePromotion(val requested: Boolean, val samsungBridge: Boolean)

internal fun widgetLivePromotion(
    context: Context,
    channel: String,
    live: Boolean,
    manufacturer: String = Build.MANUFACTURER,
): WidgetLivePromotion {
    val supported = Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
    val allowed = supported && runCatching {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.canPostPromotedNotifications() && manager.areNotificationsEnabled() &&
            (manager.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_NONE) >= NotificationManager.IMPORTANCE_LOW
    }.getOrDefault(false)
    return widgetLivePromotion(live, supported, manufacturer, allowed)
}

internal fun widgetLivePromotion(
    live: Boolean,
    supported: Boolean,
    manufacturer: String,
    allowed: Boolean,
): WidgetLivePromotion = WidgetLivePromotion(
    requested = live && supported,
    samsungBridge = live && supported && allowed && manufacturer.equals("samsung", ignoreCase = true),
)

internal fun widgetLiveNotification(
    context: Context,
    channel: String,
    content: WidgetNotificationContent,
    promotion: WidgetLivePromotion,
): Notification {
    val stop = PendingIntent.getBroadcast(context, 21, Intent(context, WidgetStopReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val open = PendingIntent.getActivity(context, 22, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_widget_notification)
        .setContentTitle(content.title)
        .setContentText(content.headline)
        .setStyle(Notification.BigTextStyle().bigText(content.detail))
        .setContentIntent(open)
        .setOngoing(true).setOnlyAlertOnce(true)
        .addAction(Notification.Action.Builder(null, appText(R.string.ui_stop_9e253, "Stop"), stop).build())
    content.subtext?.let(builder::setSubText)
    if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText(content.shortText)
    if (Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
        builder.setRequestPromotedOngoing(promotion.requested)
        if (promotion.samsungBridge) {
            // One UI has a separate Now bar eligibility path. These undocumented extras
            // bridge a standard promoted notification into it; a private `style` would
            // instead select Samsung's restricted custom-card path. See DEVELOPMENT.md.
            builder.addExtras(Bundle().apply {
                putBoolean("android.ongoingActivityNoti.automation", true)
                putString("android.ongoingActivityNoti.automationPackage", context.packageName)
            })
        }
    }
    return builder.build()
}
