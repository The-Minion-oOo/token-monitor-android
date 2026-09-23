package io.github.theminionooo.tokenmonitor.widget

import androidx.compose.ui.graphics.Color
import io.github.theminionooo.tokenmonitor.ui.Palette

internal object WidgetDeckDrawing {
    /** The dashboard's risk tones: success above 35 percent left, orange to 15, danger below. */
    fun tone(palette: Palette, remaining: Double): Color = when {
        remaining <= 15 -> palette.danger
        remaining <= 35 -> palette.orange
        else -> palette.success
    }
}
