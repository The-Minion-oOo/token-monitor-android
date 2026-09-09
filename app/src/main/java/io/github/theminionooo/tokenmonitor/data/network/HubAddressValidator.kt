package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.URI

/**
 * Limits the dashboard to private transport. A Hub secret must never be sent to
 * an arbitrary public endpoint. Tailscale is allowed by default; RFC1918 Wi-Fi
 * hosts and mDNS names require the explicit local-network switch.
 */
object HubAddressValidator {
    private const val defaultPort = 17321

    fun validate(
        rawUrl: String,
        secret: String,
        allowLocalNetwork: Boolean,
    ): HubAddressValidation {
        if (secret.isBlank()) return HubAddressValidation.Rejected("Enter the Hub secret before connecting.")
        val uri = try {
            URI(normalize(rawUrl))
        } catch (_: Exception) {
            return HubAddressValidation.Rejected("Enter the address, such as 100.101.102.103.")
        }
        if (uri.host.isNullOrBlank() && uri.scheme == null) {
            return HubAddressValidation.Rejected("Enter the address, such as 100.101.102.103.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) {
            return HubAddressValidation.Rejected("Only http and https Hub URLs are supported.")
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            return HubAddressValidation.Rejected("Use only a Hub host and optional port; do not include credentials or a path.")
        }
        if (uri.path !in listOf("", "/")) {
            return HubAddressValidation.Rejected("Use the Hub base address, without an API path.")
        }
        if (uri.port !in -1..65535 || uri.port == 0) {
            return HubAddressValidation.Rejected("Use a valid Hub port.")
        }

        val host = uri.host.lowercase().removePrefix("[").removeSuffix("]")
        val tailscale = host.endsWith(".ts.net") || isTailscaleAddress(host)
        val local = isPrivateIpv4(host) || host.endsWith(".local")
        if (!tailscale && !(allowLocalNetwork && local)) {
            return HubAddressValidation.Rejected(
                "For safety, use a Tailscale address. Enable Local Wi-Fi fallback only for a private LAN Hub.",
            )
        }
        val port = if (uri.port == -1) defaultPort else uri.port
        val displayHost = if (host.contains(':')) "[$host]" else host
        return HubAddressValidation.Allowed(
            HubConnection(
                baseUrl = "$scheme://$displayHost:$port",
                secret = secret.trim(),
                allowLocalNetwork = allowLocalNetwork,
            ),
        )
    }

    /**
     * People type what the desktop shows them, and often less: `100.101.102.103`,
     * `100.101.102.103:17321`, or a MagicDNS name. Add the scheme and drop stray
     * spaces and trailing slashes so those all work; the port defaults later.
     */
    internal fun normalize(rawUrl: String): String {
        val compact = rawUrl.filterNot { it.isWhitespace() }.trimEnd('/')
        if (compact.isEmpty()) return compact
        return if ("://" in compact) compact else "http://$compact"
    }

    /**
     * The short route name the dashboard header and Settings show for whichever address is
     * answering: the home fallback, a Tailscale address, or another allowed private network.
     */
    fun routeLabel(url: String?, fallbackUrl: String? = null): String {
        if (url == null) return "Hub"
        if (fallbackUrl != null && url == fallbackUrl) return "Home Wi-Fi"
        val host = runCatching { java.net.URI(normalize(url)).host }.getOrNull().orEmpty().trim('[', ']').lowercase()
        return when {
            host.endsWith(".ts.net") || isTailscaleAddress(host) -> "Tailscale"
            isPrivateIpv4(host) -> "Private network"
            else -> "Hub"
        }
    }

    private fun isTailscaleAddress(host: String): Boolean {
        if (host.startsWith("fd7a:115c:a1e0:", ignoreCase = true)) return true
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        return values[0] == 100 && values[1] in 64..127
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        return when {
            values[0] == 10 -> true
            values[0] == 172 && values[1] in 16..31 -> true
            values[0] == 192 && values[1] == 168 -> true
            else -> false
        }
    }
}

sealed interface HubAddressValidation {
    data class Allowed(val connection: HubConnection) : HubAddressValidation
    data class Rejected(val reason: String) : HubAddressValidation
}
