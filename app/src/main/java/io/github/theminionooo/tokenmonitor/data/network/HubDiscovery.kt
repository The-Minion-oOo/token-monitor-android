package io.github.theminionooo.tokenmonitor.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicReference

/**
 * Finds a Token Monitor Hub on the phone's current private network so nobody has to
 * type the home address. Only private ranges are searched, only the unauthenticated
 * identity endpoint is read, and it runs solely when the person taps Find.
 */
internal class HubDiscovery(
    private val probe: (baseUrl: String, timeoutMs: Int) -> Boolean = HubApiClient()::probeHub,
    private val addresses: () -> List<String> = ::deviceAddresses,
) {
    suspend fun findOnLocalNetwork(): String? = withContext(Dispatchers.IO) {
        val subnets = subnetPrefixes(addresses())
        if (subnets.isEmpty()) return@withContext null
        val candidates = subnets.flatMap { (prefix, own) ->
            (1..254).filter { it != own }.map { "http://$prefix.$it:$defaultPort" }
        }
        val winner = AtomicReference<String?>(null)
        val permits = Semaphore(parallelProbes)
        coroutineScope {
            candidates.map { url ->
                async {
                    permits.withPermit {
                        // Once one address has answered, the remaining candidates are skipped, not probed.
                        if (winner.get() == null && probe(url, probeTimeoutMs)) winner.compareAndSet(null, url)
                    }
                }
            }.awaitAll()
        }
        winner.get()
    }

    companion object {
        const val defaultPort = 17321
        // Gentle enough for a busy home router or a slow simulated network: a full sweep of a
        // silent subnet still finishes in about seven seconds, and a real Hub answers far sooner.
        private const val parallelProbes = 24
        private const val probeTimeoutMs = 600
        private const val maxSubnets = 2

        /** `/24` prefixes to search, paired with the phone's own last octet so it is skipped. */
        fun subnetPrefixes(addresses: List<String>): List<Pair<String, Int>> = addresses
            .mapNotNull { address ->
                val parts = address.split('.')
                if (parts.size != 4) return@mapNotNull null
                val octets = parts.map { it.toIntOrNull() ?: return@mapNotNull null }
                if (octets.any { it !in 0..255 }) return@mapNotNull null
                val private = octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168)
                if (!private) null else "${octets[0]}.${octets[1]}.${octets[2]}" to octets[3]
            }
            .distinctBy { it.first }
            .take(maxSubnets)

        // One interface that refuses to describe itself must not hide the others, so each is read on its own.
        private fun deviceAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList()).flatMap { network ->
            runCatching {
                if (network.isLoopback) emptyList() else network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .mapNotNull { it.hostAddress }
            }.getOrDefault(emptyList())
        }
    }
}
