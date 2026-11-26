package io.github.theminionooo.tokenmonitor.data.network

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubPairingQrTest {
    @Test fun pairingCodeCarriesTheCanonicalAddressAndRequiredConsent() {
        val tailscale = checkNotNull(decodeHubPairingQr(code("http://100.70.10.5"))?.confirm(false))
        assertEquals("http://100.70.10.5:17321", tailscale.baseUrl)
        assertEquals("synthetic-qr-secret", tailscale.secret)
        assertFalse(tailscale.allowLocalNetwork)
        assertFalse(tailscale.allowPublicHttps)

        val local = checkNotNull(decodeHubPairingQr(code("http://10.0.2.2:17647"))?.confirm(false))
        assertTrue(local.allowLocalNetwork)
        assertFalse(local.allowPublicHttps)

        val candidate = checkNotNull(decodeHubPairingQr(code("https://hub.example.org/")))
        assertTrue(candidate.requiresInternetConsent)
        assertEquals("https://hub.example.org", candidate.baseUrl)
        assertNull(candidate.confirm(false))
        val internet = checkNotNull(candidate.confirm(true))
        assertFalse(internet.allowLocalNetwork)
        assertTrue(internet.allowPublicHttps)
    }

    @Test fun scannerRejectsUnrecognizedCodesAndUnsafeDestinations() {
        val good = code("http://100.70.10.5")
        listOf(
            "https://hub.example.org",
            """{"url":"http://100.70.10.5","secret":"synthetic"}""",
            good.replace("\"version\":1", "\"version\":2"),
            good.replace("\"version\":1", "\"version\":\"1\""),
            code("http://hub.example.org"),
            code("hub.example.org"),
            code("https://203.0.113.8"),
            code("https://[2001:db8::8]"),
            code("http://10.0.2.2/api/stats"),
            code("https://user@hub.example.org"),
            code("https://hub.example.org?secret=synthetic"),
            code("http://127.0.0.1"),
            code("http://100.70.10.5", ""),
            code("http://100.70.10.5", "synthetic\r\nheader"),
            good.dropLast(1) + ",\"extra\":true}",
            "[".repeat(90) + good + "]".repeat(90),
            " ".repeat(4097),
        ).forEach { assertNull(decodeHubPairingQr(it)) }
    }

    private fun code(url: String, secret: String = "synthetic-qr-secret") = buildJsonObject {
        put("type", "token-monitor-hub")
        put("version", 1)
        put("url", url)
        put("secret", secret)
    }.toString()
}
