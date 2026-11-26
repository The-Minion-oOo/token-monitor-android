package io.github.theminionooo.tokenmonitor.data.network

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/** Owned by a single analyzer. CameraX supplies an unrotated Y plane with pixel stride 1. */
internal class HubPairingQrFrameDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    private var luminance = ByteArray(0)

    fun decode(
        plane: ByteBuffer, rowStride: Int, left: Int, top: Int, width: Int, height: Int,
    ): HubPairingQrCandidate? {
        val edge = (minOf(width, height) * 3 / 4).coerceIn(0, 960)
        if (edge == 0) return null
        val x = left + (width - edge) / 2
        val y = top + (height - edge) / 2
        val offset = plane.position() + y.toLong() * rowStride + x
        if (x < 0 || y < 0 || x.toLong() + edge > rowStride ||
            offset + (edge - 1L) * rowStride + edge > plane.limit()
        ) return null

        if (luminance.size != edge * edge) luminance = ByteArray(edge * edge)
        val input = plane.duplicate()
        for (row in 0 until edge) {
            input.position((offset + row.toLong() * rowStride).toInt())
            input.get(luminance, row * edge, edge)
        }

        val source = PlanarYUVLuminanceSource(luminance, edge, edge, 0, 0, edge, edge, false)
        return try {
            decodeHubPairingQr(reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text)
        } catch (_: ReaderException) {
            null
        }
    }
}
