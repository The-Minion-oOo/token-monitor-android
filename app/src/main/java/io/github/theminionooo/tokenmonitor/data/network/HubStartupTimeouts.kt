package io.github.theminionooo.tokenmonitor.data.network

internal enum class HubHealthConnectBudget(val timeoutMs: Int) {
    Normal(10_000), FastHomeFallback(2_000),
}

/** Only the first dashboard dial may yield sooner to an already permitted physical-LAN fallback. */
internal object HubStartupTimeouts {
    fun healthBudget(route: String, eligibleRoutes: List<String>, networks: PhoneNetworks,
                     firstDashboardAttempt: Boolean): HubHealthConnectBudget {
        if (!firstDashboardAttempt || !networks.lan || !networks.tailscaleVpn ||
            eligibleRoutes.firstOrNull() != route || !route.startsWith("http://", ignoreCase = true) ||
            !HubAddressValidator.isTailscaleRoute(route)) return HubHealthConnectBudget.Normal
        val homeRemains = eligibleRoutes.drop(1).any {
            it != route && it.startsWith("http://", ignoreCase = true) && HubAddressValidator.isLocalAddress(it)
        }
        return if (homeRemains) HubHealthConnectBudget.FastHomeFallback else HubHealthConnectBudget.Normal
    }
}
