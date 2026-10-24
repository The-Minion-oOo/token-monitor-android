package io.github.theminionooo.tokenmonitor.data.network

import java.security.cert.CertificateException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/** Safe TLS messages shared by pairing and live updates; never show raw errors. */
internal fun hubTlsMessage(error: Throwable): String? {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    return when {
        causes.any { it is SSLPeerUnverifiedException || (it is CertificateException &&
            (it.message.orEmpty().contains("subject alternative", ignoreCase = true) ||
                it.message.orEmpty().startsWith("No name matching", ignoreCase = true))) } -> "The Hub certificate does not match its HTTPS address. The secret was not sent."
        causes.any { it is CertificateException || it is CertPathValidatorException } -> "The Hub HTTPS certificate is not trusted or has expired. The secret was not sent."
        causes.any { it is SSLException } -> "A secure HTTPS connection to the Hub could not be established. The secret was not sent."
        else -> null
    }
}
