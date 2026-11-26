package io.github.theminionooo.tokenmonitor.data.network

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HubPairingQrFrameDecoderTest {
    private val url = "https://hub.example.com"
    private val raw = """{"type":"token-monitor-hub","version":1,"url":"$url","secret":"synthetic-qr-secret"}"""

    @Test fun decodesQuarterTurnsFromPaddedRowsWithoutChangingThePlane() {
        val decoder = HubPairingQrFrameDecoder()
        val code = QRCodeWriter().encode(
            raw, BarcodeFormat.QR_CODE, 180, 180,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        for (turn in 0..3) {
            val plane = ByteBuffer.allocate(7 + 360 * 410)
            plane.array().fill(0xff.toByte())
            plane.position(7)
            for (y in 0 until code.height) {
                for (x in 0 until code.width) {
                    val black = when (turn) {
                        0 -> code[x, y]
                        1 -> code[y, code.width - 1 - x]
                        2 -> code[code.width - 1 - x, code.height - 1 - y]
                        else -> code[code.height - 1 - y, x]
                    }
                    plane.put(7 + (90 + y) * 410 + 110 + x, if (black) 0 else 0xff.toByte())
                }
            }
            val candidate = checkNotNull(decoder.decode(plane, 410, 31, 27, 338, 306)) { "Could not decode rotation $turn" }
            assertEquals(url, candidate.baseUrl)
            assertTrue(candidate.requiresInternetConsent)
            assertNull(candidate.confirm(false))
            assertEquals(7, plane.position())
        }
    }

    @Test fun ignoresShortAndEmptyLuminancePlanes() {
        val decoder = HubPairingQrFrameDecoder()
        assertNull(decoder.decode(ByteBuffer.allocate(120), 128, 0, 0, 128, 128))
        val blank = ByteBuffer.wrap(ByteArray(128 * 128) { -1 })
        assertNull(decoder.decode(blank, 128, 0, 0, 128, 128))
    }
}
