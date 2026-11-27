package io.github.theminionooo.tokenmonitor.data.network

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.theminionooo.tokenmonitor.data.protocol.requireStreamJsonLimits
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A validated destination awaiting review. Decoding a public address does not authorize a connection. */
internal class HubPairingQrCandidate(private val destination: HubConnection) {
    val baseUrl get() = destination.baseUrl
    val isLocalNetwork get() = destination.allowLocalNetwork
    val requiresInternetConsent get() = destination.allowPublicHttps

    fun confirm(internetAllowed: Boolean): HubConnection? {
        if (requiresInternetConsent && !internetAllowed) return null
        return destination
    }
}

internal fun decodeHubPairingQr(raw: String): HubPairingQrCandidate? {
    if (raw.length > 4096) return null
    val payload = runCatching {
        requireStreamJsonLimits(raw)
        Json.parseToJsonElement(raw)
    }.getOrNull() as? JsonObject ?: return null
    if (payload.keys != setOf("type", "version", "url", "secret")) return null
    if (payload.text("type") != "token-monitor-hub" || payload["version"] != JsonPrimitive(1)) return null
    val url = payload.text("url")?.takeIf { it.length in 1..512 && it.none(Char::isISOControl) } ?: return null
    val secret = payload.text("secret")?.takeIf { it.length in 1..512 && it.none { char -> char < ' ' || char == '\u007f' } }
        ?: return null
    val local = HubAddressValidator.isLocalAddress(url)
    val internet = !local && !HubAddressValidator.isTailscaleRoute(url)
    if (internet && !url.startsWith("https://", ignoreCase = true)) return null
    // Validation only determines eligibility. Confirm converts the candidate into an authorized connection.
    val allowed = HubAddressValidator.validate(url, secret, local, internet) as? HubAddressValidation.Allowed
    return allowed?.connection?.let(::HubPairingQrCandidate)
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private const val MAX_IMAGE_EDGE = 2048

/** Reads only the selected image and bounds both the bitmap and the decoder's pixel buffer. */
internal fun decodeHubPairingImage(resolver: ContentResolver, uri: Uri): HubPairingQrCandidate? {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = resolver.openInputStream(uri) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while ((edge - 1) / sample + 1 > MAX_IMAGE_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        try {
            if (bitmap.width > MAX_IMAGE_EDGE || bitmap.height > MAX_IMAGE_EDGE) return null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true))
            decodeHubPairingQr(result.text)
        } finally {
            bitmap.recycle()
        }
    } catch (_: ReaderException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
