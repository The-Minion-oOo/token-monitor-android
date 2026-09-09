package io.github.theminionooo.tokenmonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/** Observes route changes only while a dashboard or explicit widget session needs updates. Does not poll. */
internal class HubNetworkObserver(context: Context, private val onChanged: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val listener = object : ConnectivityManager.NetworkCallback() {
            private var previous: Network? = manager.activeNetwork
            override fun onAvailable(network: Network) {
                // Registration itself reports the current network; startup already refreshes.
                val changed = previous != network
                previous = network
                if (changed) onChanged()
            }
            override fun onLost(network: Network) {
                if (previous == network) { previous = null; onChanged() }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(listener) }.onSuccess { callback = listener }
    }

    fun stop() {
        callback?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        callback = null
    }
}
