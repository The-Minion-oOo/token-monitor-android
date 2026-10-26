package io.github.theminionooo.tokenmonitor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.storage.DisplayOptions
import io.github.theminionooo.tokenmonitor.data.storage.WidgetRefreshPreferences

@Composable
internal fun DiagnosticActions(state: HubRepositoryState, options: DisplayOptions) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    TextButton(onClick = {
        val background = WidgetRefreshPreferences(context)
        report = diagnosticReport(state, options, background.settings, background.record, Build.VERSION.SDK_INT)
        copied = false
    }) { Text("Preview diagnostic report") }
    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("Diagnostic report") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Review before sharing. Addresses, secrets, account identities, session text and cached payloads are excluded.", style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Token Monitor diagnostic report", text))
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy report") }
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    runCatching { context.startActivity(Intent.createChooser(share, null)) }
                }) { Text("Share report") }
            } },
            confirmButton = { TextButton(onClick = { report = null }) { Text("Close") } },
        )
    }
}
