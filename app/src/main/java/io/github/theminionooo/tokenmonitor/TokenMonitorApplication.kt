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
    private var languages = ""

    override fun onCreate() {
        super.onCreate()
        io.github.theminionooo.tokenmonitor.ui.AppLanguage.bind(this)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        languages = resources.configuration.locales.toLanguageTags()
        io.github.theminionooo.tokenmonitor.widget.WidgetRefreshScheduler.reconcileAsync(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        io.github.theminionooo.tokenmonitor.ui.AppLanguage.bind(this)
        val next = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val nextLanguages = newConfig.locales.toLanguageTags()
        if (next != nightMode || nextLanguages != languages) {
            nightMode = next
            languages = nextLanguages
            thread(name = "widget-configuration") { WidgetUpdateCoordinator.refresh(applicationContext) }
        }
    }
}
