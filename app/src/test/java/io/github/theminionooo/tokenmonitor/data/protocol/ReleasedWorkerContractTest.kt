package io.github.theminionooo.tokenmonitor.data.protocol

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReleasedWorkerContractTest {
    private fun body(name: String): String {
        val generated = System.getenv("TOKEN_MONITOR_CONTRACT_DIR")?.let { File(it, "worker/$name.json") }
        if (generated != null) return generated.readText()
        return checkNotNull(javaClass.classLoader?.getResource("protocol/v0.78.0/worker/$name.json")).readText()
    }

    @Test fun workerReadRoutesProduceAnAndroidSnapshot() {
        val snapshot = HubProtocolParser.decodeSnapshot(body("health"), body("stats"), body("devices"), body("history"), body("subscriptions"), 0, false)
        assertEquals("hub", snapshot.health.role)
        assertEquals(1234L, snapshot.today.totalTokens)
        assertEquals(1, snapshot.stats.devices.size)
    }

    @Test fun workerStreamUsesTheExistingCompleteAndFreshnessDecoder() {
        val complete = HubStreamProtocol.normalizeComplete(body("stream-snapshot"))
        val fresh = HubStreamProtocol.mergeFreshness(complete, body("stream-freshness"))
        val decoded = HubProtocolParser.decodeSnapshot(body("health"), fresh, null, null, null, 0, false)
        assertEquals(1234L, decoded.today.totalTokens)
        val changed = HubProtocolParser.decodeSnapshot(body("health"), body("stream-stats"), null, null, null, 0, false)
        assertEquals(2468L, changed.today.totalTokens)
    }
}
