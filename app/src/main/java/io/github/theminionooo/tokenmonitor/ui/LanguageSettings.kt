package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.theminionooo.tokenmonitor.R

@Composable
internal fun LanguageSettings() {
    val context = LocalContext.current
    val selected = AppLanguage.selected(context)
    val system = appText(R.string.language_system, "Follow phone language")
    SettingsGroup(appText(R.string.language_title, "App language"), summary = AppLanguage.choices[selected].takeUnless { selected.isBlank() } ?: system) {
        Text(appText(R.string.language_help, "Choose a language for the app and widget labels. Tool names, reported titles and desktop notes stay as supplied by the Hub."), color = Muted, style = MaterialTheme.typography.bodySmall)
        AppLanguage.choices.forEach { (tag, label) ->
            TextButton(onClick = { if (tag != selected) AppLanguage.choose(context, tag) }, modifier = Modifier.fillMaxWidth()) {
                Text((if (tag == selected) "✓  " else "") + if (tag.isBlank()) system else label, color = if (tag == selected) Accent else Ink)
            }
        }
    }
}
