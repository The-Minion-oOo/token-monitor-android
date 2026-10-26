package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.R
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
            message = error.message ?: appText(R.string.ui_could_not_check_for_updates_ae0ba, "Could not check for updates.")
        } finally {
            checking = false
        }
    }

    LaunchedEffect(Unit) { check() }

    StatusLine("Installed", "${BuildConfig.VERSION_NAME} r${BuildConfig.VERSION_CODE % 1000}")
    StatusLine(appText(R.string.ui_source_6da13, "Source"), "GitHub Releases")
    when (val current = result) {
        is UpdateCheck.Current -> Text(
            "No newer published update. Latest release: ${current.latestTag.removePrefix("android-")}.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        is UpdateCheck.BrowserOnly -> Text(
            appText(R.string.ui_a_newer_release_is_available_open_releases_to_instal_cff94, "A newer release is available. Open Releases to install this build."),
            color = Accent,
            style = MaterialTheme.typography.bodySmall,
        )
        is UpdateCheck.Available -> {
            val release = current.release
            Text(appText(R.string.ui_1_s_r_2_s_is_ready_b65e6, "%1\$s r%2\$s is ready", release.versionName, release.versionCode % 1000), color = Ink, style = MaterialTheme.typography.bodyMedium)
            val highlights = remember(release.notes) {
                release.notes.lineSequence().map(String::trim).filter { it.startsWith("- ") }.take(3).joinToString("\n")
            }
            if (highlights.isNotBlank()) {
                Text(highlights, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(appText(R.string.ui_download_size_1_s_mb_android_will_ask_you_to_approve_f24d9, "Download size: %1\$s MB. Android will ask you to approve installation.", "%.1f".format(release.sizeBytes / 1_048_576.0)), color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    scope.launch {
                        downloading = true
                        message = null
                        try {
                            val file = downloaded ?: ReleaseUpdates.download(context, release).also { downloaded = it }
                            val installerOpened = ReleaseUpdates.install(context, file, release)
                            message = if (installerOpened) appText(R.string.ui_confirm_the_update_in_android_s_installer_06f98, "Confirm the update in Android's installer.") else appText(R.string.ui_allow_token_monitor_to_install_apps_then_tap_install_c4a45, "Allow Token Monitor to install apps, then tap Install downloaded update.")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            downloaded = null
                            message = error.message ?: appText(R.string.ui_could_not_prepare_the_update_c731d, "Could not prepare the update.")
                        } finally {
                            downloading = false
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (downloaded != null) appText(R.string.ui_install_downloaded_update_8ec10, "INSTALL DOWNLOADED UPDATE") else appText(R.string.ui_download_and_install_a0399, "DOWNLOAD AND INSTALL")) }
        }
        null -> Unit
    }
    if (checking || downloading) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(if (downloading) appText(R.string.ui_downloading_and_verifying_update_bae19, "Downloading and verifying update…") else appText(R.string.ui_checking_published_releases_8b779, "Checking published releases…"), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    message?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = { scope.launch { check() } }, enabled = !checking && !downloading, modifier = Modifier.fillMaxWidth()) {
        Text(appText(R.string.ui_check_for_updates_d34c4, "CHECK FOR UPDATES"))
    }
    OutlinedButton(onClick = onOpenReleasePage, modifier = Modifier.fillMaxWidth()) { Text(appText(R.string.ui_view_android_releases_70e50, "VIEW ANDROID RELEASES")) }
}
