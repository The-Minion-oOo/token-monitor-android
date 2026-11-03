package io.github.theminionooo.tokenmonitor.data.protocol

import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.ui.deviceHardwareIcon
import org.junit.Assert.*
import org.junit.Test

class DesktopV085Test {
    private fun stats(name: String) = listOf("", "worker/").map { prefix ->
        HubProtocolParser.decodeStats(javaClass.getResource("/protocol/v0.85.0/$prefix$name.json")!!.readText())
    }
    @Test fun `all released hardware hints reach Android`() {
        val expected = setOf("computer", "desktop", "laptop", "mini", "workstation", "server", "cloud", "linux")
        for (value in stats("hardware-kinds")) assertEquals(expected, value.devices.map { it.deviceKind }.toSet())
    }
    @Test fun `old producer updates keep the last reported hardware but explicit computer resets it`() {
        for (value in stats("hardware-retained")) assertEquals("laptop", value.devices.single().deviceKind)
        for (value in stats("hardware-reset")) assertEquals("computer", value.devices.single().deviceKind)
    }
    @Test fun `missing unknown and malformed hints use the generic default`() {
        for (name in listOf("hardware-legacy", "hardware-unknown")) for (value in stats(name)) {
            assertEquals("computer", value.devices.single().deviceKind)
        }
        assertEquals("computer", HubProtocolParser.decodeStats("""{"devices":[{"deviceId":"sample","deviceKind":42}]}""").devices.single().deviceKind)
    }
    @Test fun `reported types select hardware assets while generic values stay generic`() {
        assertEquals(R.drawable.upstream_device_laptop, deviceHardwareIcon("laptop"))
        assertEquals(R.drawable.upstream_device_mini, deviceHardwareIcon("mini"))
        assertEquals(R.drawable.upstream_device_workstation, deviceHardwareIcon("workstation"))
        assertEquals(R.drawable.upstream_device_server, deviceHardwareIcon("server"))
        assertEquals(R.drawable.upstream_device_cloud, deviceHardwareIcon("cloud"))
        assertEquals(R.drawable.upstream_device_linux, deviceHardwareIcon("linux"))
        for (kind in listOf("computer", "desktop", "unknown")) assertEquals(R.drawable.view_device, deviceHardwareIcon(kind))
    }
    @Test fun `Claude Desktop fallback and transcript priority survive shared Hub data`() {
        for (value in stats("claude-titles-shared")) {
            val sessions = value.periods.getValue("today").sessions
            assertEquals("Desktop title", sessions.single { it.id == "remote" }.title)
            assertEquals("Transcript title", sessions.single { it.id == "local" }.title)
            assertEquals(200L, value.periods.getValue("today").totalTokens)
        }
    }
    @Test fun `sharing off and revoked remove titles without losing usage`() {
        for (name in listOf("claude-titles-private", "claude-titles-revoked")) for (value in stats(name)) {
            assertTrue(value.periods.getValue("today").sessions.all { it.title.isEmpty() })
            assertEquals(200L, value.periods.getValue("today").totalTokens)
        }
    }
    @Test fun `deleted Desktop fallback leaves the native transcript title intact`() {
        for (value in stats("claude-title-removed")) {
            assertEquals("", value.periods.getValue("today").sessions.single { it.id == "remote" }.title)
            assertEquals("Transcript title", value.periods.getValue("today").sessions.single { it.id == "local" }.title)
        }
    }
    @Test fun `versioned endpoints retain the read protocol`() {
        for (prefix in listOf("", "worker/")) {
            fun response(name: String) = javaClass.getResource("/protocol/v0.85.0/$prefix$name.json")!!.readText()
            val snapshot = HubProtocolParser.decodeSnapshot(response("health"), response("stats"), response("devices"), response("history"), response("subscriptions"), 0)
            assertTrue(snapshot.health.ok)
            assertEquals(1234L, snapshot.today.totalTokens)
        }
    }
}
