package io.github.theminionooo.tokenmonitor.data.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HubRouteGuardTest {
    private val tailscale = "http://100.101.102.103:17321"
    private val magicDns = "http://desk.tail1234.ts.net:17321"
    private val home = "http://192.168.1.20:17321"
    private val mdns = "http://desktop.local:17321"
    private fun ip(vararg bytes: Int) = InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray())

    @Test
    fun `tailscale routes need a VPN and home routes need Wi-Fi or Ethernet`() {
        val mobileOnly = PhoneNetworks(lan = false, vpn = false)
        val homeWifi = PhoneNetworks(lan = true, vpn = false)
        val travelling = PhoneNetworks(lan = false, vpn = true)

        assertFalse(HubRouteGuard.usable(tailscale, mobileOnly))
        assertFalse(HubRouteGuard.usable(magicDns, homeWifi))
        assertTrue(HubRouteGuard.usable(tailscale, travelling))
        assertFalse(HubRouteGuard.usable(home, mobileOnly))
        assertFalse(HubRouteGuard.usable(mdns, travelling))
        assertTrue(HubRouteGuard.usable(home, homeWifi))
    }

    @Test
    fun `the message names whichever network is missing`() {
        assertEquals(
            "Tailscale is not connected on this phone. Turn it on to reach the Hub.",
            HubRouteGuard.unavailableMessage(listOf(tailscale), PhoneNetworks(lan = true, vpn = false)),
        )
        assertEquals(
            "This phone is not on Wi-Fi, so the home Hub address is not tried.",
            HubRouteGuard.unavailableMessage(listOf(home), PhoneNetworks(lan = false, vpn = true)),
        )
        assertEquals(
            "Tailscale is off and this phone is not on Wi-Fi, so neither Hub address can be reached.",
            HubRouteGuard.unavailableMessage(listOf(tailscale, home), PhoneNetworks(lan = false, vpn = false)),
        )
    }

    @Test
    fun `private names must resolve inside their own range before the secret is sent`() {
        val connected = PhoneNetworks(lan = true, vpn = true)
        HubRouteGuard.require(magicDns, connected) { listOf(ip(100, 88, 1, 2)) }
        HubRouteGuard.require(mdns, connected) { listOf(ip(192, 168, 1, 20)) }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(magicDns, connected) { listOf(ip(8, 8, 8, 8)) } }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(magicDns, connected) { listOf(ip(192, 168, 1, 20)) } }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(mdns, connected) { listOf(ip(192, 168, 1, 20), ip(203, 0, 113, 9)) } }
    }

    @Test
    fun `address literals and unavailable routes are checked without DNS`() {
        HubRouteGuard.require(tailscale, PhoneNetworks(lan = false, vpn = true)) { error("no lookup for a literal") }
        HubRouteGuard.require(home, PhoneNetworks(lan = true, vpn = false)) { error("no lookup for a literal") }
        assertThrows(HubApiException::class.java) {
            HubRouteGuard.require(tailscale, PhoneNetworks(lan = true, vpn = false)) { error("not reached") }
        }
    }

    @Test
    fun `tailscale IPv6 and private ranges are recognised`() {
        assertTrue(HubRouteGuard.isTailscale(InetAddress.getByName("fd7a:115c:a1e0::1")))
        assertFalse(HubRouteGuard.isTailscale(InetAddress.getByName("fd00::1")))
        assertTrue(HubRouteGuard.isPrivate(InetAddress.getByName("fd00::1")))
        assertTrue(HubRouteGuard.isPrivate(ip(169, 254, 10, 4)))
        assertFalse(HubRouteGuard.isPrivate(ip(100, 88, 1, 2)))
    }
}
