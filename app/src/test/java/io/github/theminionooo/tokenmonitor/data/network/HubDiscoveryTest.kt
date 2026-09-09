package io.github.theminionooo.tokenmonitor.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HubDiscoveryTest {
    @Test
    fun `the answering address is returned and the rest of the search is skipped`() = runBlocking {
        val probed = AtomicInteger()
        val discovery = HubDiscovery(
            probe = { url, _ ->
                probed.incrementAndGet()
                Thread.sleep(5)
                url == "http://10.0.2.2:17321"
            },
            addresses = { listOf("10.0.2.15", "10.0.2.17") },
        )

        assertEquals("http://10.0.2.2:17321", discovery.findOnLocalNetwork())
        assertTrue("expected the search to stop early, probed ${probed.get()}", probed.get() < 253)
    }

    @Test
    fun `no answer and no private network both report nothing`() = runBlocking {
        assertNull(HubDiscovery(probe = { _, _ -> false }, addresses = { listOf("192.168.1.9") }).findOnLocalNetwork())
        assertNull(HubDiscovery(probe = { _, _ -> true }, addresses = { listOf("100.101.102.103") }).findOnLocalNetwork())
    }

    @Test
    fun `only private subnets are searched and the phone's own address is skipped`() {
        val prefixes = HubDiscovery.subnetPrefixes(
            listOf("192.168.1.57", "100.101.102.103", "169.254.10.4", "10.0.2.15", "8.8.8.8", "bad.address"),
        )

        assertEquals(listOf("192.168.1" to 57, "10.0.2" to 15), prefixes)
    }

    @Test
    fun `duplicate subnets collapse and the search stays small`() {
        val prefixes = HubDiscovery.subnetPrefixes(
            listOf("192.168.1.2", "192.168.1.3", "172.20.96.1", "10.1.1.1"),
        )

        assertEquals(listOf("192.168.1" to 2, "172.20.96" to 1), prefixes)
    }
}
