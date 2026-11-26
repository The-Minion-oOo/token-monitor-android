package io.github.theminionooo.tokenmonitor.ui

import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as DialogSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.repeatOnLifecycle
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.network.HubPairingQrCandidate
import io.github.theminionooo.tokenmonitor.data.network.HubPairingQrFrameDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Composable
internal fun HubQrCameraScanner(
    owner: LifecycleOwner,
    onClose: () -> Unit,
    onUnavailable: () -> Unit,
    onRead: (HubPairingQrCandidate) -> Unit,
) {
    val context = LocalContext.current
    val rotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val read by rememberUpdatedState(onRead)
    val unavailable by rememberUpdatedState(onUnavailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        DialogSurface {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(appText(R.string.pairing_qr_scan_code, "Scan QR code"), style = MaterialTheme.typography.titleLarge)
                Text(
                    appText(R.string.pairing_qr_camera_aim, "Place the Hub pairing QR code inside the frame."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(Modifier.size(side).background(Color.Black), contentAlignment = Alignment.Center) {
                        surfaceRequest?.let { request ->
                            // The embedded surface is composited into the secure dialog window.
                            CameraXViewfinder(request, Modifier.fillMaxSize(), implementationMode = ImplementationMode.EMBEDDED)
                        }
                        Box(Modifier.size(side * 0.75f).border(2.dp, Color.White, RoundedCornerShape(12.dp)))
                    }
                }
                TextButton(onClick = onClose) { Text(appText(R.string.ui_cancel_77dfd, "Cancel")) }
            }
        }
        LaunchedEffect(owner, rotation) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val candidate = try {
                    val provider = ProcessCameraProvider.awaitInstance(context)
                    scanHubQr(provider, owner, rotation, ContextCompat.getMainExecutor(context)) { surfaceRequest = it }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } finally {
                    surfaceRequest = null
                }
                if (candidate == null) unavailable() else read(candidate)
            }
        }
    }
}

private suspend fun scanHubQr(
    provider: ProcessCameraProvider,
    owner: LifecycleOwner,
    rotation: Int,
    mainExecutor: Executor,
    onSurface: (SurfaceRequest) -> Unit,
): HubPairingQrCandidate? {
    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
        else CameraSelector.Builder().build()
    val preview = Preview.Builder().setTargetRotation(rotation).build()
    val resolution = ResolutionSelector.Builder().setResolutionStrategy(
        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
    ).build()
    val analysis = ImageAnalysis.Builder()
        .setTargetRotation(rotation)
        .setResolutionSelector(resolution)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis)
        .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).setScaleType(ViewPort.FILL_CENTER).build())
        .build()
    val worker = Executors.newSingleThreadExecutor()
    val result = CompletableDeferred<HubPairingQrCandidate?>()
    val decoder = HubPairingQrFrameDecoder()
    try {
        preview.setSurfaceProvider(mainExecutor) { request ->
            if (result.isActive) onSurface(request) else request.willNotProvideSurface()
        }
        analysis.setAnalyzer(worker) { frame ->
            try {
                if (result.isActive) {
                    val crop = frame.cropRect
                    val y = frame.planes.firstOrNull()
                    if (y != null && y.pixelStride == 1) {
                        decoder.decode(y.buffer, y.rowStride, crop.left, crop.top, crop.width(), crop.height())
                            ?.let { result.complete(it) }
                    }
                }
            } finally {
                frame.close()
            }
        }
        val camera = provider.bindToLifecycle(owner, selector, group)
        val observer = Observer<CameraState> { state ->
            if (state.error != null) result.complete(null)
        }
        camera.cameraInfo.cameraState.observe(owner, observer)
        try {
            return result.await()
        } finally {
            camera.cameraInfo.cameraState.removeObserver(observer)
        }
    } finally {
        result.cancel()
        try {
            analysis.clearAnalyzer()
            provider.unbind(preview, analysis)
        } finally {
            worker.shutdown()
        }
    }
}
