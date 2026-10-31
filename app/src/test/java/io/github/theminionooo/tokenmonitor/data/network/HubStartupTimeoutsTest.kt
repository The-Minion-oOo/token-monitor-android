package io.github.theminionooo.tokenmonitor.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HubStartupTimeoutsTest {
    private val tailscale = "http://100.64.0.9:17321"
    private val home = "http://192.168.1.20:17321"
    private val wifi = PhoneNetworks(lan = true, tailscaleVpn = true)

    @Test fun `first private dashboard dial yields sooner to an eligible home fallback`() {
        for (route in listOf(tailscale, "http://desktop.sample.ts.net:17321", "http://[fd7a:115c:a1e0::1]:17321")) {
            assertEquals(2000, budget(route, listOf(route, home)).timeoutMs)
        }
    }

    @Test fun `mobile only keeps the normal budget even with a saved subnet fallback`() {
        assertEquals(10_000, budget(networks = PhoneNetworks(lan = false, tailscaleVpn = true)).timeoutMs)
    }

    @Test fun `HTTPS primary or fallback keeps its existing connection behavior`() {
        for (route in listOf("https://hub.example.com:443", "https://desktop.sample.ts.net:443", "https://100.64.0.9:443")) {
            assertEquals(HubHealthConnectBudget.Normal, budget(route, listOf(route, home)))
        }
        assertEquals(HubHealthConnectBudget.Normal, budget(routes = listOf(tailscale, "https://home.local:443")))
    }

    @Test fun `a missing denied duplicate or non home fallback cannot shorten the dial`() {
        for (routes in listOf(listOf(tailscale), listOf(tailscale, tailscale),
            listOf(tailscale, "http://100.64.0.10:17321"), listOf(tailscale, "https://hub.example.com:443"))) {
            assertEquals(HubHealthConnectBudget.Normal, budget(routes = routes))
        }
    }

    @Test fun `a failed preferred home cannot shorten the remaining tailscale attempt`() {
        val routes = listOf(home, tailscale)
        assertEquals(HubHealthConnectBudget.Normal, budget(home, routes))
        assertEquals(HubHealthConnectBudget.Normal, budget(tailscale, routes))
    }

    @Test fun `retries widget work and pairing retain the normal budget`() {
        assertEquals(HubHealthConnectBudget.Normal, budget(firstDashboardAttempt = false))
    }

    @Test fun `missing physical LAN or tailscale network facts cannot enable the shortcut`() {
        assertEquals(HubHealthConnectBudget.Normal, budget(networks = PhoneNetworks(true, false)))
        assertEquals(HubHealthConnectBudget.Normal, budget(networks = PhoneNetworks(false, false)))
    }

    @Test fun `a private home primary keeps its normal budget`() {
        val secondHome = "http://10.0.0.20:17321"
        assertEquals(HubHealthConnectBudget.Normal, budget(home, listOf(home, secondHome)))
    }

    private fun budget(route: String = tailscale, routes: List<String> = listOf(tailscale, home),
                       networks: PhoneNetworks = wifi, firstDashboardAttempt: Boolean = true) =
        HubStartupTimeouts.healthBudget(route, routes, networks, firstDashboardAttempt)
}
