package io.github.theminionooo.tokenmonitor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withResumed
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.network.HubPairingQrCandidate
import kotlinx.coroutines.launch

private enum class ScanMessage { Denied, Settings, Unavailable }

@Composable
internal fun HubQrCameraAction(enabled: Boolean, onRead: (HubPairingQrCandidate) -> Unit) {
    val context = LocalContext.current
    val available = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    if (!available) return
    val activity = context.findActivity() ?: return
    val owner = activity as? LifecycleOwner ?: return
    val scope = rememberCoroutineScope()
    val canScan by rememberUpdatedState(enabled)
    val permission = Manifest.permission.CAMERA
    var open by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<ScanMessage?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (requesting) {
            requesting = false
            if (granted) {
                scope.launch {
                    owner.lifecycle.withResumed {
                        if (canScan && ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                        ) open = true
                    }
                }
            } else {
                message = if (activity.shouldShowRequestPermissionRationale(permission)) {
                    ScanMessage.Denied
                } else {
                    ScanMessage.Settings
                }
            }
        }
    }
    val request = {
        requesting = true
        permissionRequest.launch(permission)
    }
    val scanLabel = appText(R.string.pairing_qr_scan_code, "Scan QR code")
    val cameraNote = appText(
        R.string.pairing_qr_camera_note,
        "Token Monitor uses the camera only to read a Hub pairing QR code. The camera turns off as soon as the code is read or you leave the scanner. Nothing is saved or uploaded.",
    )
    OutlinedButton(
        enabled = enabled && !requesting,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            message = null
            when {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> open = true
                activity.shouldShowRequestPermissionRationale(permission) -> rationale = true
                else -> request()
            }
        },
    ) { Text(scanLabel) }
    Text(cameraNote, style = MaterialTheme.typography.bodySmall, color = Muted)

    message?.let { status ->
        val explanation = when (status) {
            ScanMessage.Denied, ScanMessage.Settings ->
                appText(R.string.pairing_qr_camera_denied, "Camera access is off. You can still choose a QR image.")
            ScanMessage.Unavailable ->
                appText(R.string.pairing_qr_camera_unavailable, "The camera is unavailable. Choose a QR image or try again.")
        }
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = Muted)
        if (status == ScanMessage.Settings) {
            TextButton(onClick = {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text(appText(R.string.pairing_qr_camera_settings, "Open Android settings")) }
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(scanLabel) },
            text = { Text(cameraNote) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = false
                    if (canScan) request()
                }) { Text(scanLabel) }
            },
            dismissButton = {
                TextButton(onClick = { rationale = false }) { Text(appText(R.string.ui_cancel_77dfd, "Cancel")) }
            },
        )
    }
    if (open && enabled) {
        HubQrCameraScanner(
            owner = owner,
            onClose = { open = false },
            onUnavailable = {
                open = false
                message = ScanMessage.Unavailable
            },
            onRead = { candidate ->
                if (open && canScan) {
                    open = false
                    onRead(candidate)
                }
            },
        )
    }
}
