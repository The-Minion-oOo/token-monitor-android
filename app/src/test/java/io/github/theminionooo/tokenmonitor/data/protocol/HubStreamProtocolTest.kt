package io.github.theminionooo.tokenmonitor.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStreamProtocolTest {
    @Test fun `freshness updates metadata without erasing complete stats`() {
        val complete = """{"updatedAt":"old","periods":{"today":{"totalTokens":42}},"limits":{"updatedAt":"old","providers":[{"provider":"codex"}]},"devices":[{"deviceId":"a","hostname":"Studio","updatedAt":"old","stale":false,"periods":{"today":{"totalTokens":42}}}]}"""
        val freshness = """{"stats":{"limits":{"updatedAt":"new"},"devices":[{"deviceId":"a","updatedAt":"new","receivedAt":"new","stale":true}]}}"""

        val merged = HubStreamProtocol.mergeFreshness(complete, freshness)
        val stats = HubProtocolParser.decodeStats(merged)

        assertEquals(42, stats.periods.getValue("today").totalTokens)
        assertEquals("new", stats.limits.updatedAt)
        assertEquals("codex", stats.limits.providers.single().provider)
        assertEquals("Studio", stats.devices.single().hostname)
        assertEquals("new", stats.devices.single().updatedAt)
        assertTrue(stats.devices.single().stale)
        assertEquals(42, stats.devices.single().periods.getValue("today").totalTokens)
    }
}
