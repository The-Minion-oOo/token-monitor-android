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
    fun `tailscale routes need a VPN and home routes need Wi-Fi, Ethernet or a VPN`() {
        val mobileOnly = PhoneNetworks(lan = false, tailscaleVpn = false)
        val homeWifi = PhoneNetworks(lan = true, tailscaleVpn = false)
        val travelling = PhoneNetworks(lan = false, tailscaleVpn = true)

        assertFalse(HubRouteGuard.usable(tailscale, mobileOnly))
        assertFalse(HubRouteGuard.usable(magicDns, homeWifi))
        assertTrue(HubRouteGuard.usable(tailscale, travelling))
        assertFalse(HubRouteGuard.usable(home, mobileOnly))
        // A Tailscale subnet router carries the home range when the phone is away.
        assertTrue(HubRouteGuard.usable(mdns, travelling))
        assertTrue(HubRouteGuard.usable(home, homeWifi))
    }

    @Test
    fun `the message names whichever network is missing`() {
        assertEquals(
            "Tailscale is not connected on this phone. Turn it on to reach the Hub.",
            HubRouteGuard.unavailableMessage(listOf(tailscale), PhoneNetworks(lan = true, tailscaleVpn = false)),
        )
        assertEquals(
            "This phone is not on Wi-Fi, so the home Hub address is not tried.",
            HubRouteGuard.unavailableMessage(listOf(home), PhoneNetworks(lan = false, tailscaleVpn = false)),
        )
        assertEquals(
            "Tailscale is off and this phone is not on Wi-Fi, so neither Hub address can be reached.",
            HubRouteGuard.unavailableMessage(listOf(tailscale, home), PhoneNetworks(lan = false, tailscaleVpn = false)),
        )
    }

    @Test
    fun `private names must resolve inside their own range before the secret is sent`() {
        val connected = PhoneNetworks(lan = true, tailscaleVpn = true)
        HubRouteGuard.require(magicDns, connected) { listOf(ip(100, 88, 1, 2)) }
        HubRouteGuard.require(mdns, connected) { listOf(ip(192, 168, 1, 20)) }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(magicDns, connected) { listOf(ip(8, 8, 8, 8)) } }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(magicDns, connected) { listOf(ip(192, 168, 1, 20)) } }
        assertThrows(HubApiException::class.java) { HubRouteGuard.require(mdns, connected) { listOf(ip(192, 168, 1, 20), ip(203, 0, 113, 9)) } }
        assertThrows(HubApiException::class.java) {
            HubRouteGuard.require(mdns, connected) { listOf(ip(192, 168, 1, 20), InetAddress.getByName("2001:db8::20")) }
        }
    }

    @Test
    fun `address literals and unavailable routes are checked without DNS`() {
        HubRouteGuard.require(tailscale, PhoneNetworks(lan = false, tailscaleVpn = true)) { error("no lookup for a literal") }
        HubRouteGuard.require(home, PhoneNetworks(lan = true, tailscaleVpn = false)) { error("no lookup for a literal") }
        assertThrows(HubApiException::class.java) {
            HubRouteGuard.require(tailscale, PhoneNetworks(lan = true, tailscaleVpn = false)) { error("not reached") }
        }
    }

    @Test
    fun `tailscale IPv6 and private ranges are recognised`() {
        assertTrue(HubRouteGuard.isTailscale(InetAddress.getByName("fd7a:115c:a1e0::1")))
        assertFalse(HubRouteGuard.isTailscale(InetAddress.getByName("fd00::1")))
        assertTrue(HubRouteGuard.isPrivate(InetAddress.getByName("fd00::1")))
        assertFalse(HubRouteGuard.isPrivate(ip(203, 0, 113, 9)))
        assertTrue(HubRouteGuard.isPrivate(ip(169, 254, 10, 4)))
        assertFalse(HubRouteGuard.isPrivate(ip(100, 88, 1, 2)))
        assertTrue(HubRouteGuard.isPrivate(InetAddress.getByName("fc00::1")))
        assertTrue(HubRouteGuard.isPrivate(InetAddress.getByName("fe80::1")))
        assertFalse(HubRouteGuard.isPrivate(InetAddress.getByName("2001:db8::1")))
        assertFalse(HubRouteGuard.isPrivate(InetAddress.getByName("::")))
        assertFalse(HubRouteGuard.isPrivate(InetAddress.getByName("::1")))
    }

    @Test fun `private DNS failure or no answers never authorizes a request`() {
        for (url in listOf(mdns, magicDns)) {
            assertThrows(HubApiException::class.java) { HubRouteGuard.require(url, PhoneNetworks(true, true)) { emptyList() } }
            assertThrows(HubApiException::class.java) {
                HubRouteGuard.require(url, PhoneNetworks(true, true)) { throw java.net.UnknownHostException() }
            }
        }
        HubRouteGuard.require(mdns, PhoneNetworks(true, false)) { listOf(InetAddress.getByName("fd00::1")) }
    }

    @Test fun `only VPN links holding Tailscale addresses authorize private routes on mobile`() {
        val carrier = PhoneNetworkFacts(false, false, listOf(ip(100, 88, 1, 2)))
        val unrelated = PhoneNetworkFacts(true, true, listOf(ip(10, 8, 0, 2)))
        val mobile = PhoneNetworks.from(listOf(carrier, unrelated))
        assertFalse(HubRouteGuard.usable(tailscale, mobile))
        assertFalse(HubRouteGuard.usable(home, mobile))
        for (address in listOf(ip(100, 64, 0, 1), InetAddress.getByName("fd7a:115c:a1e0::1"))) {
            val tailscaleVpn = PhoneNetworks.from(listOf(carrier, PhoneNetworkFacts(false, true, listOf(address))))
            assertTrue(HubRouteGuard.usable(tailscale, tailscaleVpn))
            assertTrue(HubRouteGuard.usable(home, tailscaleVpn))
        }
        assertTrue(PhoneNetworks.from(listOf(PhoneNetworkFacts(true, false, emptyList()))).lan)
    }

    @Test fun `unknown network information and a VPN without link addresses fail closed`() {
        for (networks in listOf(PhoneNetworks.from(null), PhoneNetworks.from(emptyList()),
            PhoneNetworks.from(listOf(PhoneNetworkFacts(false, true, emptyList()))))) {
            assertFalse(HubRouteGuard.usable(tailscale, networks))
            assertFalse(HubRouteGuard.usable(home, networks))
            assertTrue(HubRouteGuard.usable("https://hub.example.com", networks))
        }
    }
}
