package io.github.theminionooo.tokenmonitor.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import io.github.theminionooo.tokenmonitor.domain.HistoryPoint
import io.github.theminionooo.tokenmonitor.ui.Palette
import java.time.LocalDate
import kotlin.math.min

internal object WidgetDeckDrawing {
    fun surface(palette: Palette): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).drawRect(0f, 0f, 96f, 96f, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 96f, 96f,
                intArrayOf(palette.gradientTop.toArgb(), palette.shell.toArgb(), palette.gradientBottom.toArgb()),
                floatArrayOf(0f, 0.38f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
    }

    fun glow(color: Int): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                32f,
                32f,
                32f,
                intArrayOf(color and 0x66FFFFFF, color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawCircle(32f, 32f, 32f, paint)
    }

    fun bar(palette: Palette, color: Int, fraction: Double, width: Int, height: Int): Bitmap {
        val safeWidth = width.coerceIn(1, 2048)
        val safeHeight = height.coerceIn(2, 128)
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = safeHeight / 2f
            paint.color = palette.strongLine.compositeOver(palette.shell).toArgb()
            canvas.drawRoundRect(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat(), radius, radius, paint)
            val fill = (safeWidth * fraction.coerceIn(0.0, 1.0)).toFloat()
            if (fill > 0) {
                paint.color = color
                canvas.drawRoundRect(0f, 0f, fill.coerceAtLeast(safeHeight.toFloat()), safeHeight.toFloat(), radius, radius, paint)
            }
        }
    }

    fun segmentedBar(palette: Palette, rows: List<Pair<Double, Int>>, width: Int, height: Int): Bitmap {
        val safeWidth = width.coerceIn(1, 2048)
        val safeHeight = height.coerceIn(2, 128)
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = safeHeight / 2f
            paint.color = palette.strongLine.compositeOver(palette.shell).toArgb()
            canvas.drawRoundRect(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat(), radius, radius, paint)
            canvas.clipPath(Path().apply { addRoundRect(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat(), radius, radius, Path.Direction.CW) })
            var left = 0f
            rows.forEach { (fraction, color) ->
                val right = min(safeWidth.toFloat(), left + safeWidth * fraction.coerceIn(0.0, 1.0).toFloat())
                paint.color = color
                canvas.drawRect(left, 0f, right, safeHeight.toFloat(), paint)
                left = right
            }
        }
    }

    fun weekChart(points: List<HistoryPoint>, end: LocalDate, palette: Palette, width: Int, height: Int, density: Float): Bitmap {
        val safeWidth = width.coerceIn(7, 2048)
        val safeHeight = height.coerceIn(12, 1024)
        val byDay = points.associateBy { it.label.take(10) }
        val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
        val maximum = days.mapNotNull { byDay[it.toString()]?.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1L
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val column = safeWidth / 7f
            val barWidth = column * 0.56f
            val floor = safeHeight.toFloat()
            val radius = 2f * density
            days.forEachIndexed { index, day ->
                val left = index * column + (column - barWidth) / 2
                val value = byDay[day.toString()]?.tokens
                if (value == null) {
                    paint.color = palette.muted.copy(alpha = 0.5f).toArgb()
                    canvas.drawRoundRect(left, floor - 2f * density, left + barWidth, floor, radius, radius, paint)
                } else {
                    paint.color = (if (index == 6) palette.accent else palette.blue).toArgb()
                    val barHeight = (value.toDouble() / maximum * (floor - 3f * density)).toFloat().coerceAtLeast(2f * density)
                    canvas.drawRoundRect(left, floor - barHeight, left + barWidth, floor, radius, radius, paint)
                }
            }
        }
    }

    fun heatmap(points: List<HistoryPoint>, end: LocalDate, palette: Palette, width: Int, height: Int, density: Float): Bitmap {
        val safeWidth = width.coerceIn(13, 2048)
        val safeHeight = height.coerceIn(7, 1024)
        val byDay = points.associateBy { it.label.take(10) }
        val start = end.minusDays(12 * 7L + (end.dayOfWeek.value % 7).toLong())
        val values = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.mapNotNull { byDay[it.toString()]?.tokens }.toList()
        val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1L
        val columns = 13
        val gap = density.coerceAtLeast(1f)
        val cell = min((safeWidth - gap * (columns - 1)) / columns, (safeHeight - gap * 6) / 7).coerceAtLeast(1f)
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var date = start
            repeat(columns * 7) { offset ->
                val column = offset / 7
                val row = offset % 7
                val point = byDay[date.toString()]
                val ratio = point?.tokens?.toDouble()?.div(maximum) ?: 0.0
                paint.color = when {
                    point == null || point.tokens <= 0 -> palette.line.compositeOver(palette.shell).toArgb()
                    ratio >= 0.75 -> palette.accent.toArgb()
                    ratio >= 0.5 -> palette.accent.copy(alpha = 0.72f).compositeOver(palette.shell).toArgb()
                    ratio >= 0.25 -> palette.accent.copy(alpha = 0.5f).compositeOver(palette.shell).toArgb()
                    else -> palette.accent.copy(alpha = 0.28f).compositeOver(palette.shell).toArgb()
                }
                val left = column * (cell + gap)
                val top = row * (cell + gap)
                canvas.drawRoundRect(left, top, left + cell, top + cell, 1.5f * density, 1.5f * density, paint)
                date = date.plusDays(1)
            }
        }
    }

    fun tone(palette: Palette, remaining: Double): Color = when {
        remaining <= 15 -> palette.danger
        remaining <= 35 -> palette.orange
        else -> palette.success
    }
}
