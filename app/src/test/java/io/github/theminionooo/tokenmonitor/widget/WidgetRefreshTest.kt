package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshInterval
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshSettings
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshRecord
import io.github.theminionooo.tokenmonitor.data.network.RefreshNetworkFacts
import io.github.theminionooo.tokenmonitor.data.network.widgetRefreshWifiAllowed
import org.junit.Assert.*
import org.junit.Test

class WidgetRefreshTest {
    @Test fun `background consent defaults off with wifi only`() {
        assertEquals(WidgetRefreshInterval.Off, WidgetRefreshSettings().interval)
        assertTrue(WidgetRefreshSettings().wifiOnly)
        assertEquals(WidgetRefreshInterval.Off, WidgetRefreshInterval.fromMinutes(15))
        assertEquals(listOf(0, 30, 60), WidgetRefreshInterval.entries.map { it.minutes })
    }

    @Test fun `all eligibility combinations cancel or admit exactly one periodic job`() {
        for (interval in WidgetRefreshInterval.entries) for (widgets in listOf(false, true))
            for (paired in listOf(false, true)) for (rejected in listOf(false, true)) {
                val expected = if (interval.minutes > 0 && widgets && paired && !rejected) WidgetRefreshDecision.Run else WidgetRefreshDecision.Cancel
                assertEquals(expected, widgetRefreshDecision(interval, widgets, paired, rejected))
            }
    }

    @Test fun `visible dashboard live refresh and pairing checks skip rather than cancel the schedule`() {
        for (dashboard in listOf(false, true)) for (session in listOf(false, true)) for (checking in listOf(false, true)) {
            assertEquals(if (dashboard || session || checking) WidgetRefreshDecision.Skip else WidgetRefreshDecision.Run,
                widgetRefreshDecision(WidgetRefreshInterval.Sixty, true, true, false, dashboard, session, checking))
        }
        assertEquals(WidgetRefreshDecision.Cancel, widgetRefreshDecision(WidgetRefreshInterval.Off, true, true, false, true, true))
    }

    @Test fun `wifi only excludes metered wifi cellular and ethernet without a VPN`() {
        assertTrue(widgetRefreshWifiAllowed(net(wifi = true), emptyList()))
        assertFalse(widgetRefreshWifiAllowed(net(wifi = true, unmetered = false), emptyList()))
        assertFalse(widgetRefreshWifiAllowed(net(cellular = true), emptyList()))
        assertFalse(widgetRefreshWifiAllowed(net(wifi = true, cellular = true), emptyList()))
        assertFalse(widgetRefreshWifiAllowed(net(), emptyList()))
    }

    @Test fun `tailscale over unmetered wifi is allowed even when the VPN reports metered`() {
        val homeWifi = net(wifi = true)
        assertTrue(widgetRefreshWifiAllowed(net(wifi = true, vpn = true, unmetered = false), listOf(homeWifi)))
        assertTrue(widgetRefreshWifiAllowed(net(wifi = true, vpn = true), listOf(homeWifi, net(cellular = true, unmetered = false))))
    }

    @Test fun `tailscale over cellular or metered wifi stays off`() {
        assertFalse(widgetRefreshWifiAllowed(net(cellular = true, vpn = true), listOf(net(cellular = true, unmetered = false))))
        assertFalse(widgetRefreshWifiAllowed(net(wifi = true, cellular = true, vpn = true), listOf(net(wifi = true))))
        assertFalse(widgetRefreshWifiAllowed(net(wifi = true, vpn = true), listOf(net(wifi = true, unmetered = false))))
        assertFalse(widgetRefreshWifiAllowed(net(wifi = true, vpn = true), emptyList()))
    }

    @Test fun `android 8 VPN without an underlying transport needs validated unmetered wifi`() {
        val vpnOnly = net(vpn = true, unmetered = false)
        assertTrue(widgetRefreshWifiAllowed(vpnOnly, listOf(net(wifi = true), net(cellular = true, unmetered = false))))
        assertFalse(widgetRefreshWifiAllowed(vpnOnly, listOf(net(wifi = true, validated = false), net(cellular = true, unmetered = false))))
        assertFalse(widgetRefreshWifiAllowed(vpnOnly, listOf(net(cellular = true, unmetered = false))))
        assertFalse(widgetRefreshWifiAllowed(vpnOnly, emptyList()))
    }

    private fun net(wifi: Boolean = false, cellular: Boolean = false, vpn: Boolean = false, unmetered: Boolean = true, validated: Boolean = true) =
        RefreshNetworkFacts(wifi, cellular, vpn, unmetered, validated)

    @Test fun `failed run marks only the retained or older snapshot stale until newer success`() {
        val failed = WidgetRefreshRecord(successAt = 100, failureAt = 200)
        assertTrue(failed.stale(100))
        assertFalse(failed.stale(300))
        assertFalse(failed.copy(successAt = 300).stale(300))
        assertFalse(WidgetRefreshRecord().stale(0))
    }
}
