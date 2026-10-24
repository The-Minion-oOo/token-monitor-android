package io.github.theminionooo.tokenmonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What Wi-Fi only needs to know about one network. */
internal data class RefreshNetworkFacts(
    val wifi: Boolean,
    val cellular: Boolean,
    val vpn: Boolean,
    val unmetered: Boolean,
    val validated: Boolean = true,
)

/** Fail closed when Wi-Fi cannot be established, especially for a VPN over cellular. */
internal fun widgetRefreshWifiAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val active = manager.activeNetwork?.let(manager::getNetworkCapabilities)?.facts() ?: return false
    if (!active.vpn) return widgetRefreshWifiAllowed(active, emptyList())
    val physical = runCatching {
        @Suppress("DEPRECATION") manager.allNetworks.mapNotNull(manager::getNetworkCapabilities).map { it.facts() }
    }.getOrDefault(emptyList())
    return widgetRefreshWifiAllowed(active, physical)
}

/**
 * Tailscale makes the VPN the active network, and a VPN can report itself as metered whatever
 * carries it, so the decision follows the physical network underneath. Android 9 and newer copy
 * that network's transport into the VPN; Android 8 does not, so there a validated, unmetered
 * Wi-Fi network must be present, which Android prefers over mobile data as the default route.
 */
internal fun widgetRefreshWifiAllowed(active: RefreshNetworkFacts, physical: List<RefreshNetworkFacts>): Boolean {
    if (!active.vpn) return active.wifi && !active.cellular && active.unmetered
    if (active.cellular) return false
    val wifi = physical.filter { !it.vpn && it.wifi && !it.cellular && it.unmetered }
    return if (active.wifi) wifi.isNotEmpty() else wifi.any { it.validated }
}

private fun NetworkCapabilities.facts() = RefreshNetworkFacts(
    wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
    cellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    vpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
    unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
)
