package io.github.theminionooo.tokenmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.widget.WidgetNotificationContent
import io.github.theminionooo.tokenmonitor.widget.widgetLiveNotification
import io.github.theminionooo.tokenmonitor.widget.widgetLivePromotion
import org.junit.Assert.*
import org.junit.Test

class WidgetLiveNotificationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val content = WidgetNotificationContent("Token Monitor · Live", "1 running · 0 waiting",
        "1 running · 0 waiting\n100 tokens · $0.01", "Live until 15:00", "1")
    private val supported get() = Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
    private val automation = "android.ongoingActivityNoti.automation"
    private val automationPackage = "android.ongoingActivityNoti.automationPackage"

    @Test fun samsungCardKeepsItsOwnIdentityAndTheStandardControls() {
        val notification = widgetLiveNotification(context, "widget_live", content,
            widgetLivePromotion(true, supported, "Samsung", true))
        assertEquals(content.title, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(content.headline, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(content.detail, notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals("android.app.Notification\$BigTextStyle", notification.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(content.subtext, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals("Stop", notification.actions.single().title.toString())
        assertNotNull(notification.actions.single().actionIntent)
        assertNotNull(notification.contentIntent)
        val vendorKeys = notification.extras.keySet().filter { it.startsWith("android.ongoingActivityNoti.") }.toSet()
        val cardSupported = Build.VERSION.SDK_INT >= 37
        val expectedKeys = setOf(automation, automationPackage, "android.ongoingActivityNoti.style",
            "android.ongoingActivityNoti.actionType", "android.ongoingActivityNoti.primaryInfo",
            "android.ongoingActivityNoti.secondaryInfo", "android.ongoingActivityNoti.chipExpandedText",
            "android.ongoingActivityNoti.nowbarPrimaryInfo", "android.ongoingActivityNoti.chipIcon",
            "android.ongoingActivityNoti.nowbarSecondaryInfo", "android.ongoingActivityNoti.nowbarIcon",
            "android.ongoingActivityNoti.chipBgColor")
        assertEquals(if (cardSupported) expectedKeys else emptySet<String>(), vendorKeys)
        if (cardSupported) {
            assertTrue(notification.extras.getBoolean(automation))
            assertEquals(context.packageName, notification.extras.getString(automationPackage))
            assertEquals(1, notification.extras.getInt("android.ongoingActivityNoti.style"))
            assertEquals(content.headline, notification.extras.getString("android.ongoingActivityNoti.primaryInfo"))
            assertEquals("Token Monitor\nLive until 15:00", notification.extras.getString("android.ongoingActivityNoti.secondaryInfo"))
            val lockIcon = notification.extras.getParcelable("android.ongoingActivityNoti.nowbarIcon", android.graphics.drawable.Icon::class.java)!!
            assertEquals(context.packageName, lockIcon.resPackage)
            assertEquals(R.mipmap.ic_launcher, lockIcon.resId)
            assertEquals(content.subtext, notification.extras.getString("android.ongoingActivityNoti.nowbarSecondaryInfo"))
            assertFalse("Samsung uses its native card instead of AOSP promotion", notification.hasPromotableCharacteristics())
            assertNotNull(notification.contentView)
            assertNotNull(notification.bigContentView)
            fun texts(view: View): List<String> = when (view) {
                is TextView -> listOf(view.text.toString())
                is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = notification.bigContentView.apply(context, FrameLayout(context))
                val labels = texts(view)
                assertTrue("The platform layout retains the app name", labels.contains("Token Monitor"))
                assertTrue(labels.contains(content.detail))
                assertTrue(labels.contains("Stop"))
                assertFalse(labels.any { it.contains("Gemini Intelligence") })
            }
        } else {
            assertNull(notification.contentView)
            assertNull(notification.bigContentView)
        }
    }

    @Test fun refreshBlockedPromotionAndOtherPhonesKeepTheOrdinaryPath() {
        data class Case(val live: Boolean, val supported: Boolean, val manufacturer: String, val allowed: Boolean)
        for (case in listOf(
            Case(true, supported, "Google", true),
            Case(true, supported, "samsung", false),
            Case(true, false, "samsung", true),
            Case(false, supported, "samsung", true),
        )) {
            val promotion = widgetLivePromotion(case.live, case.supported, case.manufacturer, case.allowed)
            val notification = widgetLiveNotification(context, "widget_live", content, promotion)
            assertFalse("No vendor bridge for $case", notification.extras.containsKey(automation))
            assertFalse(notification.extras.containsKey(automationPackage))
            if (supported) assertEquals("Standard eligibility for $case",
                case.live && case.supported, notification.hasPromotableCharacteristics())
            assertEquals("Stop", notification.actions.single().title.toString())
        }
        // Settings changes must invalidate notification equality even when the Hub text is unchanged.
        assertNotEquals(widgetLivePromotion(true, true, "samsung", true),
            widgetLivePromotion(true, true, "samsung", false))
    }

    @Test fun samsungBridgeFollowsTheActualPromotionSettingAndChannelImportance() {
        check(context.packageName.endsWith(".preview"))
        if (Build.VERSION.SDK_INT < 37) {
            assertEquals(widgetLivePromotion(true, supported, "samsung", false),
                widgetLivePromotion(context, "chip-policy-low", true, "samsung"))
            return
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val previous = Regex("POST_PROMOTED_NOTIFICATIONS: (allow|ignore|deny|default|foreground)")
            .find(shell("cmd appops get ${context.packageName} POST_PROMOTED_NOTIFICATIONS"))?.groupValues?.get(1) ?: "default"
        val manager = context.getSystemService(NotificationManager::class.java)
        val emulator = shell("getprop ro.kernel.qemu").trim() == "1"
        fun awaitScreen(awake: Boolean) {
            val power = context.getSystemService(android.os.PowerManager::class.java)
            val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
            val end = android.os.SystemClock.elapsedRealtime() + 3000
            while ((power.isInteractive != awake || awake && keyguard.isKeyguardLocked) && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(25)
            assertEquals(awake, power.isInteractive)
            if (awake) assertFalse(keyguard.isKeyguardLocked)
        }
        try {
            manager.createNotificationChannel(NotificationChannel("chip-policy-low", "Chip policy", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel("chip-policy-min", "Chip policy minimized", NotificationManager.IMPORTANCE_MIN))
            shell("cmd appops set ${context.packageName} POST_PROMOTED_NOTIFICATIONS allow")
            assertTrue(manager.canPostPromotedNotifications())
            val allowed = widgetLivePromotion(context, "chip-policy-low", true, "samsung")
            assertTrue(allowed.samsungBridge)
            assertFalse(widgetLivePromotion(context, "chip-policy-min", true, "samsung").samsungBridge)
            assertFalse(widgetLivePromotion(context, "missing-chip-channel", true, "samsung").samsungBridge)
            if (emulator) {
                shell("input keyevent KEYCODE_SLEEP")
                awaitScreen(false)
                val asleep = widgetLivePromotion(context, "chip-policy-low", true, "samsung")
                assertFalse(asleep.samsungBridge)
                val fallback = widgetLiveNotification(context, "chip-policy-low", content, asleep)
                assertFalse(fallback.extras.containsKey(this.automation))
                assertNull(fallback.contentView)
                assertTrue(fallback.hasPromotableCharacteristics())
                shell("input keyevent KEYCODE_WAKEUP")
                shell("wm dismiss-keyguard")
                awaitScreen(true)
                assertTrue(widgetLivePromotion(context, "chip-policy-low", true, "samsung").samsungBridge)
            }
            shell("cmd appops set ${context.packageName} POST_PROMOTED_NOTIFICATIONS ignore")
            assertFalse(manager.canPostPromotedNotifications())
            val blocked = widgetLivePromotion(context, "chip-policy-low", true, "samsung")
            assertFalse(blocked.samsungBridge)
            assertNotEquals(allowed, blocked)
            val notification = widgetLiveNotification(context, "chip-policy-low", content, blocked)
            assertFalse(notification.extras.containsKey(this.automation))
            assertEquals("Stop", notification.actions.single().title.toString())
        } finally {
            if (emulator) {
                shell("input keyevent KEYCODE_WAKEUP")
                shell("wm dismiss-keyguard")
            }
            shell("cmd appops set ${context.packageName} POST_PROMOTED_NOTIFICATIONS $previous")
            manager.deleteNotificationChannel("chip-policy-low")
            manager.deleteNotificationChannel("chip-policy-min")
        }
    }
}
