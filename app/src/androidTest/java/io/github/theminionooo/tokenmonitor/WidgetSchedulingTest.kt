package io.github.theminionooo.tokenmonitor

import android.app.job.JobScheduler
import android.app.job.JobInfo
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.*
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.Assert.*
import org.junit.Test

class WidgetSchedulingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }

    @Test fun scheduleTracksConsentIntervalNetworkPairingWidgetsAndRejection() {
        check(context.packageName.endsWith(".preview"))
        val prefs = WidgetRefreshPreferences(context)
        val pairing = SecureConnectionStore(context)
        val jobs = context.getSystemService(JobScheduler::class.java)
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 7804)
        val user = shell("am get-current-user")
        fun pending() = jobs.getPendingJob(WidgetRefreshScheduler.JOB_ID)
        try {
            prefs.preferences.edit().clear().commit()
            pairing.clear()
            WidgetRefreshScheduler.reconcile(context)
            assertNull(pending())
            pairing.save(HubConnection("http://100.64.0.100:17321", "synthetic-scheduler-secret", false))
            prefs.save(WidgetRefreshSettings(WidgetRefreshInterval.Thirty))
            WidgetRefreshScheduler.reconcile(context)
            assertNull(pending()) // No placed widget.
            shell("appwidget grantbind --package ${context.packageName} --user $user")
            val id = host.allocateAppWidgetId()
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, SwipeWidgetProvider::class.java)))
            WidgetRefreshScheduler.reconcile(context)
            assertEquals(30 * 60_000L, pending()?.intervalMillis)
            // Wi-Fi only is checked by the run itself, so a VPN that reports metered cannot block it.
            assertEquals(JobInfo.NETWORK_TYPE_ANY, pending()?.networkType)
            assertTrue(pending()?.isPersisted == true)
            prefs.save(WidgetRefreshSettings(WidgetRefreshInterval.Sixty, false))
            WidgetRefreshScheduler.reconcile(context)
            assertEquals(60 * 60_000L, pending()?.intervalMillis)
            assertEquals(JobInfo.NETWORK_TYPE_ANY, pending()?.networkType)
            prefs.failure(200, true)
            prefs.success(100) // A delayed older save must not clear rejection.
            WidgetRefreshScheduler.reconcile(context)
            assertNull(pending())
            prefs.success(300)
            WidgetRefreshScheduler.reconcile(context)
            assertNotNull(pending())
            pairing.clear()
            WidgetRefreshScheduler.reconcile(context)
            assertNull(pending())
        } finally {
            prefs.save(WidgetRefreshSettings())
            pairing.clear()
            prefs.clearRecord()
            host.deleteHost()
            WidgetRefreshScheduler.reconcile(context)
            shell("appwidget revokebind --package ${context.packageName} --user $user")
        }
    }
}
