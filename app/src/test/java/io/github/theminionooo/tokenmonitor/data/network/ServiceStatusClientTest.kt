package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.ServiceHealth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStatusClientTest {
    private val provider = ServiceStatusProvider(
        id = "example",
        label = "Example",
        pageUrl = "https://status.example.com",
        summaryUrl = "https://status.example.com/api/v2/summary.json",
    )

    @Test
    fun `healthy provider is operational with no active issue counts`() {
        val status = summarizeStatuspageProvider(
            provider,
            payload(
                """
                {
                  "status": { "indicator": "none", "description": "All Systems Operational" },
                  "components": [{ "name": "API", "status": "operational" }],
                  "incidents": [{ "name": "Old incident", "status": "resolved" }],
                  "scheduled_maintenances": [{ "name": "Old maintenance", "status": "completed" }]
                }
                """,
            ),
            checkedAt = 123L,
        )

        assertEquals(ServiceHealth.Ok, status.health)
        assertEquals("All Systems Operational", status.description)
        assertEquals(0, status.affectedComponents)
        assertEquals(0, status.incidentCount)
        assertEquals(0, status.maintenanceCount)
    }

    @Test
    fun `degraded provider leads with active incident and counts affected components`() {
        val status = summarizeStatuspageProvider(
            provider,
            payload(
                """
                {
                  "status": { "indicator": "minor", "description": "Partial System Outage" },
                  "components": [
                    { "name": "API", "status": "degraded_performance" },
                    { "name": "Web", "status": "under_maintenance" }
                  ],
                  "incidents": [
                    { "name": "Elevated API errors", "status": "investigating" },
                    { "name": "Old incident", "status": "resolved" }
                  ],
                  "scheduled_maintenances": [{ "name": "Database work", "status": "in_progress" }]
                }
                """,
            ),
            checkedAt = 456L,
        )

        assertEquals(ServiceHealth.Degraded, status.health)
        assertEquals("Elevated API errors", status.description)
        assertEquals(1, status.affectedComponents)
        assertEquals(1, status.incidentCount)
        assertEquals(1, status.maintenanceCount)
    }

    private fun payload(value: String) = Json.parseToJsonElement(value).jsonObject
}
