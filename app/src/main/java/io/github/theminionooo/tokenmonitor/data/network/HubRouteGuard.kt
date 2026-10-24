package io.github.theminionooo.tokenmonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Which kinds of network the phone is on right now. Read from Android on demand, never polled.
 * When Android cannot say, every route stays allowed so a missing answer never strands the dashboard.
 */
internal data class PhoneNetworks(val lan: Boolean, val vpn: Boolean) {
    companion object {
        private val unknown = PhoneNetworks(lan = true, vpn = true)

        fun read(context: Context): PhoneNetworks {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return unknown
            val capabilities = runCatching {
                @Suppress("DEPRECATION") manager.allNetworks.mapNotNull(manager::getNetworkCapabilities)
            }.getOrNull() ?: return unknown
            return PhoneNetworks(
                lan = capabilities.any { it.isLan() },
                vpn = capabilities.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
            )
        }

        /** Private IPv4 addresses the phone holds on Wi-Fi or Ethernet, for Find. Mobile data and VPN tunnels are never searched. */
        fun lanAddresses(context: Context): List<String> {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
            return runCatching {
                @Suppress("DEPRECATION") manager.allNetworks.filter { network ->
                    manager.getNetworkCapabilities(network)?.let { it.isLan() && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } == true
                }.flatMap { network ->
                    manager.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address }
                        .filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
                }
            }.getOrDefault(emptyList())
        }

        private fun NetworkCapabilities.isLan() =
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

/**
 * Keeps the Hub secret on the network a saved route belongs to. A Tailscale address is tried only while a
 * VPN is up, because without Tailscale the 100.64/10 range is carrier-grade NAT. A home address is tried on
 * Wi-Fi or Ethernet, or through a VPN such as a Tailscale subnet router, but never directly over mobile data.
 * Names are resolved first so a hostile DNS answer cannot point a private name at a public host.
 */
internal object HubRouteGuard {
    private const val privateNameMessage = "The Hub name did not resolve to a private address, so the secret was not sent."

    fun usable(url: String, networks: PhoneNetworks): Boolean = when {
        HubAddressValidator.isTailscaleRoute(url) -> networks.vpn
        HubAddressValidator.isLocalAddress(url) -> networks.lan || networks.vpn
        else -> true
    }

    /** Why none of [routes] can be tried on [networks]. */
    fun unavailableMessage(routes: List<String>, networks: PhoneNetworks): String {
        val tailscaleOff = routes.any(HubAddressValidator::isTailscaleRoute) && !networks.vpn
        val offLan = routes.any(HubAddressValidator::isLocalAddress) && !networks.lan && !networks.vpn
        return when {
            tailscaleOff && offLan -> "Tailscale is off and this phone is not on Wi-Fi, so neither Hub address can be reached."
            tailscaleOff -> "Tailscale is not connected on this phone. Turn it on to reach the Hub."
            else -> "This phone is not on Wi-Fi, so the home Hub address is not tried."
        }
    }

    /** Throws before any request when [url] cannot be used on [networks] or its name resolves outside the route's range. */
    fun require(url: String, networks: PhoneNetworks, allowPublicHttps: Boolean = false, resolve: (String) -> List<InetAddress> = ::resolveAll) {
        val validation = HubAddressValidator.validate(url, "route-check", true, allowPublicHttps)
        if (validation is HubAddressValidation.Rejected) throw HubApiException(0, validation.reason)
        if (!usable(url, networks)) throw HubApiException(0, unavailableMessage(listOf(url), networks))
        val host = runCatching { URI(url).host }.getOrNull()?.trim('[', ']')?.lowercase() ?: return
        if (isLiteral(host)) return
        val tailscale = HubAddressValidator.isTailscaleRoute(url)
        if (!tailscale && !HubAddressValidator.isLocalAddress(url)) return
        // An unresolvable name fails in the HTTP client with its own message.
        val addresses = runCatching { resolve(host) }.getOrNull().orEmpty()
        if (addresses.any { if (tailscale) !isTailscale(it) else !isPrivate(it) }) throw HubApiException(0, privateNameMessage)
    }

    private fun resolveAll(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

    private fun isLiteral(host: String) = ':' in host || host.split('.').let { parts -> parts.size == 4 && parts.all { it.toIntOrNull() != null } }

    internal fun isTailscale(address: InetAddress): Boolean {
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            is Inet4Address -> bytes[0] == 100 && bytes[1] in 64..127
            is Inet6Address -> bytes.take(6) == listOf(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0)
            else -> false
        }
    }

    /** mDNS hosts on IPv6 home networks also publish global addresses, so only IPv4 answers must be private. */
    internal fun isPrivate(address: InetAddress): Boolean = address !is Inet4Address || address.isSiteLocalAddress || address.isLinkLocalAddress
}
