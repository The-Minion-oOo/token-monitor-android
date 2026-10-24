package io.github.theminionooo.tokenmonitor.data.network

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import org.junit.Assert.*
import org.junit.Test

class HubTlsHandshakeTest {
    @Test fun untrustedCertificateAndHostnameMismatchStopBeforeHttpHeaders() {
        val directory = Files.createTempDirectory("synthetic-hub-tls-").toFile()
        val storeFile = directory.resolve("synthetic.p12")
        val password = "synthetic-test-only"
        val keytool = java.io.File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
        var server: HttpsServer? = null
        val originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        try {
            val process = ProcessBuilder(keytool.path, "-genkeypair", "-alias", "synthetic", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=synthetic.invalid", "-validity", "1", "-storetype", "PKCS12", "-keystore", storeFile.path,
                "-storepass", password, "-keypass", password, "-ext", "SAN=dns:synthetic.invalid", "-noprompt").redirectErrorStream(true)
                .redirectOutput(directory.resolve("keytool.log")).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Synthetic certificate generation timed out") }
            assertEquals(0, process.exitValue())
            val store = KeyStore.getInstance("PKCS12").apply { storeFile.inputStream().use { load(it, password.toCharArray()) } }
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
            val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
            val host = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .first { it is Inet4Address && it.isSiteLocalAddress }.hostAddress
            val httpRequests = AtomicInteger()
            server = HttpsServer.create(InetSocketAddress(host, 0), 0).apply {
                httpsConfigurator = HttpsConfigurator(tls)
                createContext("/") { exchange -> httpRequests.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close() }
                start()
            }
            val connection = HubConnection("https://$host:${server.address.port}", "synthetic-secret", true)
            HubApiClient().use { client ->
                val error = assertThrows(SSLHandshakeException::class.java) { client.getStats(connection) }
                assertTrue(hubTlsMessage(error)!!.contains("certificate"))
            }
            // Only the JVM test trusts its disposable certificate so hostname verification
            // can be tested independently. Production Android trust config is untouched.
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val trustedTls = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
            HttpsURLConnection.setDefaultSSLSocketFactory(trustedTls.socketFactory)
            HubApiClient().use { client ->
                val error = assertThrows(SSLHandshakeException::class.java) { client.getStats(connection) }
                assertTrue(hubTlsMessage(error)!!.contains("does not match"))
            }
            assertEquals("TLS must fail before an HTTP request or Authorization header reaches the server", 0, httpRequests.get())
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(originalFactory)
            server?.stop(0)
            // Files were created exclusively in this exact test-owned temporary directory.
            directory.listFiles()?.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }
}
