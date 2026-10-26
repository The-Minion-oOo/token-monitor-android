package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun `reports allowlisted state without serializing addresses raw errors or stored identities`() {
        val state = HubRepositoryState(hasConnection = true, connectionUrl = "https://private-marker.example.com", fallbackUrl = "http://10.90.80.70",
            activeUrl = "https://private-marker.example.com", allowPublicHttps = true, message = "NEVER_INCLUDE_BEARER_OR_ACCOUNT@example.invalid")
        val report = diagnosticReport(state, DisplayOptions(hiddenTools = setOf("PRIVATE_TOOL"), hiddenLimitItems = setOf("PRIVATE_ACCOUNT")), WidgetRefreshSettings(), WidgetRefreshRecord(successAt = 1, failureAt = 2), 37)
        listOf("private-marker", "10.90.80.70", "NEVER_INCLUDE", "example.invalid", "PRIVATE_TOOL", "PRIVATE_ACCOUNT").forEach { assertFalse(report.contains(it)) }
        assertTrue(report.contains("route_type: internet-https"))
        assertTrue(report.contains("connection_state: error-details-omitted"))
        assertTrue(report.contains("display_hidden_tools_count: 1"))
    }
    @Test fun `unpaired and rejected-secret diagnostics remain useful without adding secret data`() {
        val report = diagnosticReport(HubRepositoryState(secretRejected = true), DisplayOptions(), WidgetRefreshSettings(), WidgetRefreshRecord(secretRejected = true), 26)
        assertTrue(report.contains("paired: false"))
        assertTrue(report.contains("connection_state: secret-rejected"))
        assertTrue(report.contains("android_api: 26"))
    }
}
