package io.github.theminionooo.tokenmonitor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.ui.HubPairingQrEntry
import io.github.theminionooo.tokenmonitor.ui.appText
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HubPairingQrInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importingAnImageOnlyConnectsAfterAddressConfirmation() {
        val address = "http://10.0.2.2:17321"
        withPairingImage(address) { pick, paired ->
            pick()
            assertNull(paired.get())
            compose.onNodeWithText(internetSwitch).assertDoesNotExist()
            compose.onNodeWithText(appText(R.string.ui_cancel_77dfd, "Cancel")).performClick()
            assertNull(paired.get())
            pick()
            compose.onNodeWithText(connectLabel).performClick()
            val connection = checkNotNull(paired.get())
            assertEquals(address, connection.baseUrl)
            assertEquals("synthetic-qr-secret", connection.secret)
            assertTrue(connection.allowLocalNetwork)
        }
    }

    @Test fun internetQrRequiresFreshOptInBeforeConnecting() {
        val address = "https://hub.example.com"
        withPairingImage(address) { pick, paired ->
            pick()
            compose.onNodeWithText(internetSwitch).assertIsOff()
            compose.onNodeWithText(connectLabel).assertIsNotEnabled()
            assertNull(paired.get())

            compose.onNodeWithText(internetSwitch).performClick().assertIsOn()
            assertNull(paired.get())
            compose.onNodeWithText(appText(R.string.ui_cancel_77dfd, "Cancel")).performClick()
            assertNull(paired.get())

            pick()
            compose.onNodeWithText(internetSwitch).assertIsOff()
            compose.onNodeWithText(connectLabel).assertIsNotEnabled()
            compose.onNodeWithText(internetSwitch).performClick()
            compose.onNodeWithText(connectLabel).performClick()
            val connection = checkNotNull(paired.get())
            assertEquals(address, connection.baseUrl)
            assertTrue(connection.allowPublicHttps)
        }
    }

    private val connectLabel get() = appText(R.string.ui_connect_6e288, "Connect")
    private val internetSwitch get() =
        appText(R.string.ui_allow_an_internet_hub_over_https_fcbdd, "Allow an internet Hub over HTTPS")

    private fun withPairingImage(
        address: String,
        verify: (() -> Unit, AtomicReference<HubConnection?>) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "usage-reports").apply { mkdirs() }
        val file = File.createTempFile("test-pairing-", ".png", folder)
        val secret = "synthetic-qr-secret"
        val raw = """{"type":"token-monitor-hub","version":1,"url":"$address","secret":"$secret"}"""
        val matrix = QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 360, 360)
        val pixels = IntArray(360 * 360) { i -> if (matrix[i % 360, i / 360]) Color.BLACK else Color.WHITE }
        val bitmap = Bitmap.createBitmap(pixels, 360, 360, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: ActivityResultContract<I, O>,
                        input: I,
                        options: ActivityOptionsCompat?,
                    ) {
                        dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
                    }
                }
            }
            val paired = AtomicReference<HubConnection?>()
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    HubPairingQrEntry(enabled = true, onConnect = paired::set)
                }
            }
            fun pick() {
                compose.onNodeWithText(appText(R.string.pairing_qr_pick_image, "Choose QR image")).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(address).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(address).assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText(secret).fetchSemanticsNodes().isEmpty())
            }

            verify(::pick, paired)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
