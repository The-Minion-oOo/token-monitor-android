package io.github.theminionooo.tokenmonitor.ui

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.data.network.decodeHubPairingImage
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HubPairingQrEntry(enabled: Boolean, onConnect: (HubConnection) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importedHub by remember { mutableStateOf<HubConnection?>(null) }
    var invalidCode by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { image ->
        if (image != null) {
            reading = true
            scope.launch {
                try {
                    importedHub = withContext(Dispatchers.IO) { decodeHubPairingImage(context.contentResolver, image) }
                    invalidCode = importedHub == null
                } finally {
                    reading = false
                }
            }
        }
    }
    OutlinedButton(
        onClick = {
            invalidCode = false
            pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        },
        enabled = enabled && !reading,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(appText(R.string.pairing_qr_pick_image, "Choose QR image")) }
    if (invalidCode) {
        Text(appText(R.string.pairing_qr_invalid, "This is not a supported Hub pairing QR. Try another code or enter the details below."),
            color = DangerText, style = MaterialTheme.typography.bodySmall)
    }
    importedHub?.let { hub ->
        HubPairingQrReview(hub, onDismiss = { importedHub = null }, onConnect = {
            importedHub = null
            if (enabled) onConnect(hub)
        })
    }
}

@Composable
internal fun HubPairingQrReview(hub: HubConnection, onDismiss: () -> Unit, onConnect: () -> Unit) {
    val explanation = when {
        hub.allowLocalNetwork -> appText(R.string.pairing_qr_local, "Connect allows access to this Hub on your private network.")
        hub.allowPublicHttps -> appText(R.string.pairing_qr_internet, "Connect allows encrypted access to this Hub over the internet.")
        else -> appText(R.string.pairing_qr_tailscale, "Connect uses Tailscale to reach this Hub.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appText(R.string.pairing_qr_review, "Connect to this Hub?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hub.baseUrl)
                Text(explanation)
            }
        },
        confirmButton = { TextButton(onClick = onConnect) { Text(appText(R.string.ui_connect_6e288, "Connect")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(appText(R.string.ui_cancel_77dfd, "Cancel")) } },
    )
}
