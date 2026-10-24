package io.github.theminionooo.tokenmonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Fail closed when Wi-Fi cannot be established, especially for a VPN over cellular. */
internal fun widgetRefreshWifiAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val active = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(active) ?: return false
    return widgetRefreshWifiAllowed(
        wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}

internal fun widgetRefreshWifiAllowed(wifi: Boolean, cellular: Boolean, unmetered: Boolean) = wifi && !cellular && unmetered
