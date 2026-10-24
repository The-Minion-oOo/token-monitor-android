package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.URI

/**
 * Tailscale is the default. LAN routes and internet HTTPS have separate consent;
 * an internet target must be a public DNS name with no URL credentials or path.
 */
object HubAddressValidator {
    private const val defaultPort = 17321

    fun validate(
        rawUrl: String,
        secret: String,
        allowLocalNetwork: Boolean,
        allowPublicHttps: Boolean = false,
    ): HubAddressValidation {
        if (secret.isBlank()) return HubAddressValidation.Rejected("Enter the Hub secret before connecting.")
        val uri = try {
            val normalized = normalize(rawUrl)
            val initial = URI(normalized)
            val publicName = !isTailscaleRoute(normalized) && !isLocalAddress(normalized)
            URI(if (allowPublicHttps && publicName && "://" !in rawUrl) normalized.replaceFirst("http://", "https://") else initial.toString())
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

        val host = uri.host.lowercase().removePrefix("[").removeSuffix("]").removeSuffix(".")
        val tailscale = host.endsWith(".ts.net") || isTailscaleAddress(host)
        val local = isPrivateIpv4(host) || host.endsWith(".local")
        val internet = !tailscale && !local
        if (internet) {
            if (scheme != "https") return HubAddressValidation.Rejected("Internet Hubs require https://. The secret was not sent.")
            if (!allowPublicHttps) return HubAddressValidation.Rejected("Turn on Allow an internet Hub over HTTPS, or use Tailscale (recommended).")
            if (!publicDnsName(host)) return HubAddressValidation.Rejected("Use a public DNS name for an internet Hub, without an IP address or private-use name.")
        } else if (!tailscale && !(allowLocalNetwork && local)) {
            return HubAddressValidation.Rejected(
                "Use a Tailscale address, or enable a private Wi-Fi address as the main Hub.",
            )
        }
        val port = if (uri.port == -1) { if (internet) 443 else defaultPort } else uri.port
        val displayHost = if (host.contains(':')) "[$host]" else host
        val authority = if (internet && port == 443) displayHost else "$displayHost:$port"
        return HubAddressValidation.Allowed(
            HubConnection(
                baseUrl = "$scheme://$authority",
                secret = secret.trim(),
                allowLocalNetwork = allowLocalNetwork,
                allowPublicHttps = allowPublicHttps,
            ),
        )
    }

    /** Revalidate persisted targets independently of the form before opening any socket. */
    internal fun requireAllowed(connection: HubConnection) {
        if (connection.baseUrl == connection.fallbackUrl &&
            !isLocalAddress(connection.baseUrl) && !isTailscaleRoute(connection.baseUrl)) {
            throw HubApiException(0, "The home fallback must use a private address.")
        }
        val result = validate(connection.baseUrl, connection.secret,
            connection.allowLocalNetwork || connection.baseUrl == connection.fallbackUrl, connection.allowPublicHttps)
        if (result is HubAddressValidation.Rejected) throw HubApiException(0, result.reason)
    }

    private fun publicDnsName(host: String): Boolean {
        val labels = host.split('.')
        if (host.length > 253 || labels.size < 2) return false
        if (labels.any { !it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) }) return false
        // A numeric or hexadecimal-looking last label can be interpreted as an IP literal.
        if (labels.last().all(Char::isDigit) || labels.last().startsWith("0x")) return false
        val privateSuffixes = setOf("local", "localhost", "localdomain", "internal", "lan", "home", "arpa", "intranet", "corp", "onion", "alt", "invalid", "test", "example")
        return labels.last() !in privateSuffixes
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
            runCatching { URI(normalize(url)).scheme == "https" && publicDnsName(host.removeSuffix(".")) }.getOrDefault(false) -> "Internet (HTTPS)"
            else -> "Hub"
        }
    }

    /** True only for the private LAN forms accepted by [validate], never the Tailscale range. */
    fun isLocalAddress(url: String): Boolean {
        val host = runCatching { URI(normalize(url)).host }.getOrNull().orEmpty().trim('[', ']').lowercase().removeSuffix(".")
        return isPrivateIpv4(host) || host.endsWith(".local")
    }

    /** True for a Tailscale address or MagicDNS name, which only routes while Tailscale is connected. */
    fun isTailscaleRoute(url: String): Boolean {
        val host = runCatching { URI(normalize(url)).host }.getOrNull().orEmpty().trim('[', ']').lowercase().removeSuffix(".")
        return host.endsWith(".ts.net") || isTailscaleAddress(host)
    }

    private fun isTailscaleAddress(host: String): Boolean {
        if (host.startsWith("fd7a:115c:a1e0:", ignoreCase = true)) return true
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in 0..255 }) return false
        return values[0] == 100 && values[1] in 64..127
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in 0..255 }) return false
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
