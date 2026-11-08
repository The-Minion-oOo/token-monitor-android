package io.github.theminionooo.tokenmonitor.widget

import android.app.Notification
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.graphics.drawable.Icon
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
    // Samsung's lock-screen automation surface substitutes its assistant identity,
    // even with an explicit icon. Only use the card while the phone is unlocked.
    val allowed = Build.VERSION.SDK_INT >= 37 && runCatching {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.canPostPromotedNotifications() && manager.areNotificationsEnabled() &&
            (manager.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_NONE) >= NotificationManager.IMPORTANCE_LOW &&
            context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
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
    }
    val notification = builder.build()
    if (Build.VERSION.SDK_INT >= 37 && promotion.samsungBridge) {
        // Render the platform's own layouts BEFORE adding automation. Samsung otherwise
        // replaces the app heading with "Gemini Intelligence". No custom layout or
        // hidden method is used. The Samsung card is a separate promotion path; these
        // pre-rendered views intentionally fall back to a normal Android notification.
        @Suppress("DEPRECATION")
        notification.contentView = builder.createContentView()
        @Suppress("DEPRECATION")
        notification.bigContentView = builder.createBigContentView()
        @Suppress("DEPRECATION")
        notification.headsUpContentView = builder.createHeadsUpContentView()
        notification.extras.putAll(Bundle().apply {
            putBoolean("android.ongoingActivityNoti.automation", true)
            putString("android.ongoingActivityNoti.automationPackage", context.packageName)
            putInt("android.ongoingActivityNoti.style", 1)
            putInt("android.ongoingActivityNoti.actionType", 1)
            putString("android.ongoingActivityNoti.primaryInfo", content.headline)
            putString("android.ongoingActivityNoti.secondaryInfo", listOfNotNull(context.getString(R.string.app_name), content.subtext).joinToString("\n"))
            putString("android.ongoingActivityNoti.chipExpandedText", content.shortText)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", content.headline)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", content.subtext.orEmpty())
            putParcelable("android.ongoingActivityNoti.nowbarIcon", Icon.createWithResource(context, R.mipmap.ic_launcher))
            putParcelable("android.ongoingActivityNoti.chipIcon", Icon.createWithResource(context, R.drawable.ic_widget_notification))
            putInt("android.ongoingActivityNoti.chipBgColor", 0xff1e6cff.toInt())
        })
    }
    return notification
}
