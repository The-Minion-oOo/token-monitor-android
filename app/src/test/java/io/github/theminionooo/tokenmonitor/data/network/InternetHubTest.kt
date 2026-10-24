package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class InternetHubTest {
    private val secret = "synthetic-secret"
    private fun validate(url: String, enabled: Boolean = true, local: Boolean = false) =
        HubAddressValidator.validate(url, secret, local, enabled)

    @Test fun existingConnectionsAndValidationDefaultToPrivateRoutes() {
        assertFalse(HubConnection("https://hub.example.com", secret, false).allowPublicHttps)
        assertTrue(HubAddressValidator.validate("https://hub.example.com", secret, false) is HubAddressValidation.Rejected)
        assertTrue(validate("https://hub.example.com", enabled = false) is HubAddressValidation.Rejected)
    }

    @Test fun publicNamesUseHttpsAndTheStandardPort() {
        for ((input, expected) in listOf(
            "hub.example.com" to "https://hub.example.com",
            "https://Hub.Example.com:443/" to "https://hub.example.com",
            "hub.example.com:8443" to "https://hub.example.com:8443",
            "https://hub.example.de" to "https://hub.example.de",
            "https://hub.example.ca" to "https://hub.example.ca",
        )) {
            val connection = (validate(input) as HubAddressValidation.Allowed).connection
            assertEquals(input, expected, connection.baseUrl)
            assertTrue(connection.allowPublicHttps)
        }
        assertEquals("Internet (HTTPS)", HubAddressValidator.routeLabel("https://hub.example.com"))
        assertEquals("Home Wi-Fi", HubAddressValidator.routeLabel("http://192.168.1.2:17321", "http://192.168.1.2:17321"))
    }

    @Test fun publicHttpIsRefusedByEveryBoundaryBeforeOpeningARequest() {
        for (url in listOf("http://hub.example.com", "http://hub.example.com:443", "http://203.0.113.1")) {
            for (enabled in listOf(false, true)) {
                assertTrue(validate(url, enabled, local = true) is HubAddressValidation.Rejected)
                assertThrows(HubApiException::class.java) {
                    HubRouteGuard.require(url, PhoneNetworks(lan = true, vpn = true), enabled) { error("DNS must not run") }
                }
                HubApiClient { error("Refusal must happen before the request callback or socket") }.use { client ->
                    assertThrows(HubApiException::class.java) { client.getStats(HubConnection(url, secret, true, allowPublicHttps = enabled)) }
                }
            }
        }
    }

    @Test fun publicHttpsRequiresConsentEvenForTamperedSavedConnections() {
        assertThrows(HubApiException::class.java) {
            HubRouteGuard.require("https://hub.example.com", PhoneNetworks(false, false))
        }
        HubApiClient { error("No socket without consent") }.use { client ->
            assertThrows(HubApiException::class.java) { client.getStats(HubConnection("https://hub.example.com", secret, false)) }
        }
        HubRouteGuard.require("https://hub.example.com", PhoneNetworks(false, false), true) { error("TLS verifies the public name") }
    }

    @Test fun onlyPublicDnsNamesAreAcceptedAsInternetRoutes() {
        for (url in listOf(
            "https://203.0.113.1", "https://127.0.0.1", "https://[2001:db8::1]", "https://[::1]",
            "https://localhost", "https://hub", "https://hub.local", "https://hub.local.",
            "https://hub.internal", "https://hub.lan", "https://router.home.arpa", "https://hub.localdomain",
            "https://hub.onion", "https://hub.corp", "https://hub.invalid", "https://a..com", "https://-hub.example.com",
            "https://127.1", "https://2130706433", "https://0x7f000001", "https://0177.0.0.1",
            "https://user:pass@hub.example.com", "https://hub.example.com/path", "https://hub.example.com?q=secret",
            "https://hub.example.com#fragment", "ftp://hub.example.com", "https://hub.example.com:0",
        )) {
            assertTrue(url, validate(url) is HubAddressValidation.Rejected)
            if (!HubAddressValidator.isLocalAddress(url)) {
                assertThrows(url, HubApiException::class.java) { HubRouteGuard.require(url, PhoneNetworks(true, true), true) }
            }
        }
    }

    @Test fun internetOptInDoesNotEnableHomeWifiAndPrivateRoutesKeepTheirPorts() {
        assertTrue(validate("https://192.168.1.2") is HubAddressValidation.Rejected)
        for ((input, local, expected) in listOf(
            Triple("100.88.1.2", false, "http://100.88.1.2:17321"),
            Triple("desk.tail123.ts.net", false, "http://desk.tail123.ts.net:17321"),
            Triple("desktop.local", true, "http://desktop.local:17321"),
        )) assertEquals(expected, (validate(input, local = local) as HubAddressValidation.Allowed).connection.baseUrl)
    }

    @Test fun tlsErrorsHaveSafeDistinctMessages() {
        assertTrue(hubTlsMessage(SSLPeerUnverifiedException("private diagnostic"))!!.contains("does not match"))
        val certificateError = SSLHandshakeException("private diagnostic").apply { initCause(CertificateException("synthetic")) }
        assertTrue(hubTlsMessage(certificateError)!!.contains("not trusted or has expired"))
        assertTrue(hubTlsMessage(SSLHandshakeException("private diagnostic"))!!.contains("secure HTTPS"))
        assertFalse(hubTlsMessage(certificateError)!!.contains("private diagnostic"))
        assertNull(hubTlsMessage(java.net.ConnectException("offline")))
    }
}
