package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.BuildConfig
import io.github.theminionooo.tokenmonitor.data.update.ReleaseUpdates
import io.github.theminionooo.tokenmonitor.data.update.UpdateCheck
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdatesPanel(onOpenReleasePage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheck?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun check() {
        checking = true
        message = null
        result = null
        downloaded = null
        try {
            result = ReleaseUpdates.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            message = error.message ?: "Could not check for updates."
        } finally {
            checking = false
        }
    }

    LaunchedEffect(Unit) { check() }

    StatusLine("Installed", "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}")
    StatusLine("Source", "GitHub Releases")
    when (val current = result) {
        is UpdateCheck.Current -> Text(
            "No newer published update. Latest release: ${current.latestTag.removePrefix("android-")}.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        is UpdateCheck.BrowserOnly -> Text(
            "A newer release is available. Open Releases to install this build.",
            color = Accent,
            style = MaterialTheme.typography.bodySmall,
        )
        is UpdateCheck.Available -> {
            val release = current.release
            Text("${release.versionName} r${release.versionCode % 1000} is ready", color = Ink, style = MaterialTheme.typography.bodyMedium)
            val highlights = remember(release.notes) {
                release.notes.lineSequence().map(String::trim).filter { it.startsWith("- ") }.take(3).joinToString("\n")
            }
            if (highlights.isNotBlank()) {
                Text(highlights, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("Download size: ${"%.1f".format(release.sizeBytes / 1_048_576.0)} MB. Android will ask you to approve installation.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    scope.launch {
                        downloading = true
                        message = null
                        try {
                            val file = downloaded ?: ReleaseUpdates.download(context, release).also { downloaded = it }
                            val installerOpened = ReleaseUpdates.install(context, file, release)
                            message = if (installerOpened) "Confirm the update in Android's installer." else "Allow Token Monitor to install apps, then tap Install downloaded update."
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            downloaded = null
                            message = error.message ?: "Could not prepare the update."
                        } finally {
                            downloading = false
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (downloaded != null) "INSTALL DOWNLOADED UPDATE" else "DOWNLOAD AND INSTALL") }
        }
        null -> Unit
    }
    if (checking || downloading) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(if (downloading) "Downloading and verifying update…" else "Checking published releases…", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    message?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = { scope.launch { check() } }, enabled = !checking && !downloading, modifier = Modifier.fillMaxWidth()) {
        Text("CHECK FOR UPDATES")
    }
    OutlinedButton(onClick = onOpenReleasePage, modifier = Modifier.fillMaxWidth()) { Text("VIEW ANDROID RELEASES") }
}
