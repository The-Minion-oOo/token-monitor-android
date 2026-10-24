package io.github.theminionooo.tokenmonitor

import android.app.Application
import android.content.res.Configuration
import io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator
import kotlin.concurrent.thread

/**
 * Reconcile opted-in widget scheduling without starting a connection. Redraw saved widgets
 * when the phone switches between light and dark while this process is alive.
 */
class TokenMonitorApplication : Application() {
    private var nightMode = 0

    override fun onCreate() {
        super.onCreate()
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        io.github.theminionooo.tokenmonitor.widget.WidgetRefreshScheduler.reconcileAsync(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val next = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (next != nightMode) {
            nightMode = next
            thread(name = "widget-night-mode") { WidgetUpdateCoordinator.refresh(applicationContext) }
        }
    }
}
