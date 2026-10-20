package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

@Composable
internal fun HomeListCountControl(module: String, count: Int, onChange: (Int) -> Unit) {
    Column {
        Text(if (count == 1) "1 item" else "$count items", color = Muted, style = MaterialTheme.typography.labelSmall)
        Slider(
            value = count.coerceIn(1, 50).toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(1, 50)) },
            valueRange = 1f..50f,
            steps = 48,
            colors = SliderDefaults.colors(
                thumbColor = Success,
                activeTrackColor = Success,
                inactiveTrackColor = Line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics { contentDescription = "$module items on Home" },
        )
    }
}
