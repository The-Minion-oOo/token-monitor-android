package io.github.theminionooo.tokenmonitor.data

import io.github.theminionooo.tokenmonitor.data.network.HubApiException
import org.junit.Assert.*
import org.junit.Test

class ReconnectDelayTest {
    @Test fun `a notice waits three seconds and retries keep the original deadline`() {
        val delay = ReconnectDelay()
        assertNull(delay.remainingMillis(100))
        delay.failed(100)
        assertEquals(3_000L, delay.remainingMillis(100))
        delay.failed(1_100)
        assertEquals(1L, delay.remainingMillis(3_099))
        assertEquals(0L, delay.remainingMillis(3_100))
        delay.failed(5_000)
        assertEquals(0L, delay.remainingMillis(6_000))
    }

    @Test fun `fresh data cancels a short interruption and starts a separate outage`() {
        val delay = ReconnectDelay()
        delay.failed(1_000)
        delay.recovered()
        assertNull(delay.remainingMillis(5_000))
        delay.failed(6_000)
        assertEquals(3_000L, delay.remainingMillis(6_000))
    }

    @Test fun `lasting causes are named while brief outages keep the generic notice`() {
        val certificate = javax.net.ssl.SSLHandshakeException("handshake").apply {
            initCause(java.security.cert.CertificateException("expired"))
        }
        assertEquals("The Hub HTTPS certificate is not trusted or has expired. The secret was not sent.",
            reconnectNoticeCause(certificate))
        val route = HubApiException(0, "Tailscale is not connected.")
        assertEquals("Tailscale is not connected.", reconnectNoticeCause(route))
        val missing = HubApiException(404, "This Hub endpoint is not available. Update the desktop Token Monitor Hub.")
        assertEquals(missing.presentation, reconnectNoticeCause(missing))
        for (status in listOf(500, 503, 408, 429)) {
            assertNull("HTTP $status is temporary", reconnectNoticeCause(HubApiException(status, "The Hub returned HTTP $status.")))
        }
        assertNull(reconnectNoticeCause(java.net.SocketTimeoutException("timeout")))
        assertNull(reconnectNoticeCause(java.io.IOException("connection reset")))
    }
}
