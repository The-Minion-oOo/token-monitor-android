package io.github.theminionooo.tokenmonitor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.ui.InterfaceTheme
import io.github.theminionooo.tokenmonitor.ui.Palette
import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatCompactTokens
import io.github.theminionooo.tokenmonitor.ui.formatMoney
import io.github.theminionooo.tokenmonitor.ui.formatTokens
import io.github.theminionooo.tokenmonitor.ui.originalToolColor
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import io.github.theminionooo.tokenmonitor.ui.upstreamToolAsset
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the selected deck page into one immutable frame. The launcher receives a
 * single ImageView, so it cannot add collection-card depth or resize each page.
 */
internal object WidgetDeckRenderer {
    internal const val CARD_ASPECT = 1.82f
    private const val MAX_BITMAP_WIDTH = 560

    internal fun frameSize(size: SizeF): SizeF {
        val height = min(size.height, size.width / CARD_ASPECT).coerceAtLeast(1f)
        val width = min(size.width, height * CARD_ASPECT).coerceAtLeast(1f)
        return SizeF(width, height)
    }

    fun render(
        context: Context,
        page: WidgetDeckPage,
        data: WidgetDeckData,
        theme: InterfaceTheme,
        size: SizeF,
    ): RemoteViews {
        val bitmap = renderBitmap(context, page, data, Palette.from(theme), size)
        return RemoteViews(context.packageName, R.layout.usage_widget_deck).apply {
            setImageViewBitmap(R.id.swipe_page_bitmap, bitmap)
            setContentDescription(R.id.swipe_page_bitmap, description(context, page, data))
            setContentDescription(R.id.swipe_page_open, description(context, page, data))
            setContentDescription(R.id.swipe_page_previous, "Previous Token Monitor page")
            setContentDescription(R.id.swipe_page_next, "Next Token Monitor page")
            setContentDescription(R.id.widget_refresh, if (data.status == "UPDATING") "Refreshing widget" else "Refresh widget now")
            setContentDescription(R.id.widget_live, if (data.liveEnabled) "Turn widget Live off" else "Turn widget Live on for one hour")
        }
    }

    internal fun renderBitmap(
        context: Context,
        page: WidgetDeckPage,
        data: WidgetDeckData,
        palette: Palette,
        size: SizeF,
    ): Bitmap {
        val frame = frameSize(size)
        val renderScale = min(2f, MAX_BITMAP_WIDTH / frame.width).coerceAtLeast(1f)
        val width = (frame.width * renderScale).roundToInt().coerceAtLeast(1)
        val height = (frame.height * renderScale).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(width / frame.width, height / frame.height)
            DeckCanvas(context, canvas, frame.width, frame.height, palette).draw(page, data)
        }
    }

    private fun description(context: Context, page: WidgetDeckPage, data: WidgetDeckData): String {
        val summary = data.snapshot?.let { "${formatTokens(it.today.totalTokens)} tokens. ${formatMoney(it.today.costUsd)} today." }.orEmpty()
        return "Token Monitor ${context.getString(page.subtitle)}. $summary ${data.statusDescription}. Tap the left or right edge to change page, or tap the content to open."
    }
}

private class DeckCanvas(
    private val context: Context,
    private val canvas: Canvas,
    private val width: Float,
    private val height: Float,
    private val palette: Palette,
) {
    private val mono = Typeface.create("monospace", Typeface.NORMAL)
    private val monoBold = Typeface.create("monospace", Typeface.BOLD)
    private val sansBold = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val compact = width < 300f || height < 150f
    private val sectionSize = if (compact) 8.25f else 10.5f
    private val primarySize = if (compact) 11.25f else 13.5f
    private val bodySize = if (compact) 7.25f else 9.5f
    private val secondarySize = if (compact) 6.75f else 8.5f
    private val contentEdge = if (compact) 16f else 24f
    private val ink = palette.ink.toArgb()
    private val muted = palette.muted.toArgb()
    private val readableMuted = lerp(palette.muted, palette.ink, 0.18f).toArgb()
    private val line = palette.line.compositeOver(palette.shell).toArgb()
    private val strongLine = palette.strongLine.compositeOver(palette.shell).toArgb()

    fun draw(page: WidgetDeckPage, data: WidgetDeckData) {
        drawSurface()
        drawHeader(page, data)
        if (!data.hasData) drawCentered(data.emptyMessage) else when (page) {
            WidgetDeckPage.Overview -> drawOverview(data)
            WidgetDeckPage.Limits -> drawLimits(data)
            WidgetDeckPage.Breakdown -> drawBreakdown(data)
            WidgetDeckPage.Activity -> drawActivity(data)
        }
        drawPager(page)
    }

    private fun drawPager(page: WidgetDeckPage) {
        val centerY = height / 2f
        val chevronColor = palette.muted.copy(alpha = if (compact) 0.62f else 0.78f).compositeOver(palette.shell).toArgb()
        drawChevron(if (compact) 6.5f else 8f, centerY, pointsLeft = true, color = chevronColor)
        drawChevron(width - if (compact) 6.5f else 8f, centerY, pointsLeft = false, color = chevronColor)

        val gap = if (compact) 6f else 7f
        val dotY = height - if (compact) 6f else 9f
        val startX = width / 2f - gap * (WidgetDeckPage.entries.size - 1) / 2f
        WidgetDeckPage.entries.forEachIndexed { index, candidate ->
            circle(
                startX + index * gap,
                dotY,
                if (candidate == page) 2f else 1.5f,
                if (candidate == page) palette.accent.toArgb() else strongLine,
            )
        }
    }

    private fun drawChevron(centerX: Float, centerY: Float, pointsLeft: Boolean, color: Int) {
        val halfWidth = if (compact) 2f else 2.6f
        val halfHeight = if (compact) 3.5f else 4.5f
        val pointX = centerX + if (pointsLeft) -halfWidth else halfWidth
        val outerX = centerX + if (pointsLeft) halfWidth else -halfWidth
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = if (compact) 1.2f else 1.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(outerX, centerY - halfHeight, pointX, centerY, paint)
        canvas.drawLine(pointX, centerY, outerX, centerY + halfHeight, paint)
    }

    private fun drawSurface() {
        val radius = min(20f, height / 7f)
        val card = RectF(0.75f, 0.75f, width - 0.75f, height - 0.75f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width, height,
                intArrayOf(palette.gradientTop.toArgb(), palette.shell.toArgb(), palette.gradientBottom.toArgb()),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(card, radius, radius, fill)
        canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strongLine
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })
    }

    private fun drawHeader(page: WidgetDeckPage, data: WidgetDeckData) {
        val iconSize = if (compact) 18f else 22f
        drawDrawable(R.drawable.token_monitor_icon, 12f, 10f, iconSize, iconSize)
        val titleX = 12f + iconSize + 8f
        val brandSize = if (compact) 9f else 12f
        text("TOKEN MONITOR", titleX, 10f + brandSize, brandSize, ink, monoBold, width = if (compact) 83f else 145f)
        text(
            context.getString(page.subtitle).uppercase(),
            titleX, if (compact) 36f else 41f, if (compact) 7f else 9f, muted, mono,
            width = if (compact) 100f else 150f,
        )
        val toggleRight = width - 12f
        val toggleWidth = if (compact) 35f else 42f
        val toggleTop = if (compact) 10f else 12f
        val toggleHeight = if (compact) 18f else 22f
        val toggleLeft = toggleRight - toggleWidth
        val refreshSize = if (compact) 17f else 21f
        val refreshLeft = toggleLeft - if (compact) 55f else 78f
        drawDrawable(R.drawable.ic_widget_refresh, refreshLeft, toggleTop, refreshSize, refreshSize, ink)
        if (!compact) {
            text(data.status, toggleLeft - 8f, toggleTop + 15.5f, 9.5f, if (data.liveEnabled && data.status == "LIVE") palette.accent.toArgb() else readableMuted, monoBold, Paint.Align.RIGHT, 58f)
        }
        val activeColor = palette.accent.toArgb()
        val fillColor = if (data.liveEnabled) palette.accent.copy(alpha = 0.18f).compositeOver(palette.shell).toArgb() else palette.overlay.compositeOver(palette.shell).toArgb()
        roundRect(toggleLeft, toggleTop, toggleRight, toggleTop + toggleHeight, toggleHeight / 2f, fillColor)
        strokeRoundRect(toggleLeft, toggleTop, toggleRight, toggleTop + toggleHeight, toggleHeight / 2f, if (data.liveEnabled) activeColor else strongLine)
        val knobRadius = toggleHeight * 0.31f
        val knobX = if (data.liveEnabled) toggleRight - toggleHeight / 2f else toggleLeft + toggleHeight / 2f
        circle(knobX, toggleTop + toggleHeight / 2f, knobRadius, if (data.liveEnabled) activeColor else readableMuted)
    }

    private fun drawOverview(data: WidgetDeckData) {
        val snapshot = requireNotNull(data.snapshot)
        if (compact) {
            text("TOTAL TOKENS", contentEdge, 55f, sectionSize, ink, monoBold)
            fitText(formatTokens(snapshot.today.totalTokens), contentEdge, 82f, 23f, 13f, ink, sansBold, width - contentEdge * 2)
            text("${formatMoney(snapshot.today.costUsd)} estimated cost", contentEdge, 99f, bodySize, muted, mono, width = width - contentEdge * 2)
            return
        }
        val left = contentEdge
        val rightColumn = width - 118f
        text("TOTAL TOKENS", left, 70f, sectionSize, ink, monoBold)
        fitText(formatTokens(snapshot.today.totalTokens), left, 116f, 31f, 20f, ink, sansBold, rightColumn - left - 12f)
        text("${formatMoney(snapshot.today.costUsd)} estimated cost", left, 137f, bodySize, readableMuted, mono, width = rightColumn - left - 12f)
        line(rightColumn, 61f, rightColumn, 145f, strongLine)
        data.stats.take(3).forEachIndexed { index, stat ->
            val top = 65f + index * 25f
            text(stat.first, rightColumn + 12f, top + 12f, primarySize, ink, monoBold, width = width - rightColumn - contentEdge - 12f)
            text(stat.second, rightColumn + 12f, top + 22f, secondarySize, readableMuted, mono, width = width - rightColumn - contentEdge - 12f)
        }
        val colors = listOf(palette.blue, palette.orange, palette.yellow)
        val colored = data.tools.take(3).mapIndexed { index, row -> row to originalToolColor(row.name, colors[index]).toArgb() }
        drawSegmentedBar(contentEdge, 145f, width - contentEdge, 151f, colored.map { it.first.share to it.second })
        val slot = (width - contentEdge * 2) / 3f
        colored.forEachIndexed { index, entry ->
            val label = "${entry.first.name.displayName().substringBefore(' ')} ${(entry.first.share * 100).roundToInt()}%"
            text(label, contentEdge + index * slot, 163f, secondarySize, entry.second, mono, width = slot - 5f)
        }
        val weekTokens = data.week.sumOf { it.tokens }
        val weekCost = data.week.sumOf { it.costUsd }
        text("THIS WEEK  ${formatCompactTokens(weekTokens)} · ${formatMoney(weekCost)}", contentEdge, height - 20f, bodySize, if (weekTokens > 0) ink else muted, mono, width = width - contentEdge * 2)
    }

    private fun drawLimits(data: WidgetDeckData) {
        if (data.limits.isEmpty()) {
            drawCentered(context.getString(R.string.widget_no_limits))
            return
        }
        val top = if (compact) 48f else 59f
        val bottom = height - if (compact) 8f else 15f
        val centerX = width / 2f
        line(centerX, top, centerX, bottom, line)
        val rowHeight = (bottom - top) / 2f
        line(contentEdge, top + rowHeight, width - contentEdge, top + rowHeight, line)
        data.limits.take(4).forEachIndexed { index, row ->
            val column = index % 2
            val lineIndex = index / 2
            val cellLeft = if (column == 0) contentEdge else centerX + 10f
            val cellRight = if (column == 0) centerX - 10f else width - contentEdge
            val cellTop = top + lineIndex * rowHeight + 5f
            val color = WidgetDeckDrawing.tone(palette, row.remainingPercent).toArgb()
            drawMark(row.provider, cellLeft, cellTop, if (compact) 11f else 14f, color, R.drawable.view_limits)
            text("${row.provider.providerLabel()} · ${row.title}", cellLeft + if (compact) 15f else 19f, cellTop + 10f, bodySize, ink, mono, width = cellRight - cellLeft - 20f)
            text("${row.remainingPercent.roundToInt()}% left", cellLeft, cellTop + if (compact) 25f else 29f, primarySize, if (row.remainingPercent > 35) ink else color, monoBold, width = cellRight - cellLeft)
            val barTop = cellTop + if (compact) 29f else 35f
            drawBar(cellLeft, barTop, cellRight, barTop + 4f, row.remainingPercent / 100.0, color)
            text(row.reset.ifBlank { "Reset not reported" }, cellLeft, barTop + 14f, secondarySize, readableMuted, mono, width = cellRight - cellLeft)
        }
    }

    private fun drawBreakdown(data: WidgetDeckData) {
        if (data.tools.isEmpty() && data.models.isEmpty()) {
            drawCentered("No tool or model breakdown reported")
            return
        }
        val top = if (compact) 51f else 62f
        val bottom = height - if (compact) 8f else 16f
        val middle = width / 2f
        line(middle, top, middle, bottom, strongLine)
        text("TOOLS", contentEdge, top + 10f, sectionSize, ink, monoBold)
        text("MODELS", middle + 12f, top + 10f, sectionSize, ink, monoBold)
        val toolsTop = top + 17f
        val toolSlots = data.tools.take(3).size.coerceAtLeast(2)
        val toolRow = (bottom - toolsTop) / toolSlots
        data.tools.take(3).forEachIndexed { index, row ->
            val y = toolsTop + index * toolRow
            val color = originalToolColor(row.name, listOf(palette.blue, palette.orange, palette.yellow)[index]).toArgb()
            drawMark(row.name, contentEdge, y + 2f, if (compact) 10f else 14f, color, R.drawable.view_tool)
            text(row.name.displayName(), contentEdge + if (compact) 14f else 19f, y + 12f, bodySize + if (compact) 0f else 0.5f, ink, mono, width = middle - contentEdge - 62f)
            text("${formatCompactTokens(row.tokens)} · ${(row.share * 100).roundToInt()}%", middle - 12f, y + 12f, secondarySize, readableMuted, mono, Paint.Align.RIGHT, 66f)
            drawBar(contentEdge, y + if (compact) 17f else 20f, middle - 12f, y + if (compact) 20f else 24f, row.share, color)
        }
        val modelsTop = top + 17f
        val modelSlots = data.models.take(4).size.coerceAtLeast(2)
        val modelRow = (bottom - modelsTop) / modelSlots
        data.models.take(4).forEachIndexed { index, row ->
            val y = modelsTop + index * modelRow
            val fallback = listOf(palette.blue, palette.orange, palette.purple, palette.yellow)[index]
            val color = originalToolColor(row.name, fallback).toArgb()
            drawMark(row.name, middle + 12f, y + 1f, if (compact) 9f else 13f, color, R.drawable.view_model)
            text(row.name, middle + if (compact) 25f else 30f, y + 11f, bodySize + if (compact) 0f else 0.5f, ink, mono, width = width - middle - if (compact) 100f else 111f)
            text("${formatCompactTokens(row.tokens)} · ${(row.share * 100).roundToInt()}%", width - contentEdge, y + 11f, secondarySize, readableMuted, mono, Paint.Align.RIGHT, 74f)
            drawBar(middle + 12f, y + if (compact) 15f else 18f, width - contentEdge, y + if (compact) 18f else 22f, row.share, color)
        }
    }

    private fun drawActivity(data: WidgetDeckData) {
        if (data.history.isEmpty()) {
            drawCentered("No activity history reported")
            return
        }
        val top = if (compact) 50f else 62f
        val bottom = height - if (compact) 8f else 16f
        val middle = width * 0.57f
        line(middle, top, middle, bottom, strongLine)
        val weekTokens = data.week.sumOf { it.tokens }
        val weekCost = data.week.sumOf { it.costUsd }
        val peak = data.week.maxOfOrNull { it.tokens } ?: 0
        fun compactTokens(value: Long) = formatCompactTokens(value).replace(".0K", "K").replace(".0M", "M")
        text("7D ${compactTokens(weekTokens)} · ${formatMoney(weekCost)}", contentEdge, top + 11f, bodySize, ink, monoBold, width = middle - contentEdge - 86f)
        text("DAY PEAK ${compactTokens(peak)}", middle - 10f, top + 11f, secondarySize, readableMuted, mono, Paint.Align.RIGHT, 84f)
        val chartTop = top + 18f
        drawBitmap(WidgetDeckDrawing.weekChart(data.history, data.date, palette, ceil(middle - contentEdge - 12f).toInt(), ceil(bottom - chartTop).toInt(), 1f), contentEdge, chartTop, middle - 12f, bottom)
        val rightLeft = middle + 12f
        text("13 WEEKS", rightLeft, top + 11f, sectionSize, ink, monoBold)
        val heatTop = top + 18f
        val statsTop = bottom - if (compact) 27f else 35f
        drawBitmap(WidgetDeckDrawing.heatmap(data.history, data.date, palette, ceil(width - rightLeft - contentEdge).toInt(), ceil(statsTop - heatTop - 5f).toInt(), 1f), rightLeft, heatTop, width - contentEdge, statsTop - 5f)
        val statWidth = (width - rightLeft - contentEdge) / 2f
        text(data.activeDays.toString(), rightLeft, statsTop + 13f, primarySize, ink, monoBold)
        text("ACTIVE DAYS", rightLeft, statsTop + 24f, secondarySize, readableMuted, mono, width = statWidth - 3f)
        text(formatCompactTokens(data.messagesToday), rightLeft + statWidth, statsTop + 13f, primarySize, ink, monoBold)
        text("MESSAGES", rightLeft + statWidth, statsTop + 24f, secondarySize, readableMuted, mono, width = statWidth)
    }

    private fun drawCentered(message: String) {
        val maxWidth = width - contentEdge * 2
        val words = message.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        val paint = textPaint(bodySize, muted, mono)
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
            else { lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        val lineHeight = if (compact) 11f else 14f
        val start = height / 2f - (lines.size - 1) * lineHeight / 2f
        lines.take(3).forEachIndexed { index, value -> text(value, width / 2f, start + index * lineHeight, bodySize, muted, mono, Paint.Align.CENTER, maxWidth) }
    }

    private fun drawMark(name: String, left: Float, top: Float, size: Float, color: Int, fallback: Int) {
        drawDrawable(upstreamToolAsset(name) ?: fallback, left, top, size, size, color)
    }

    private fun drawDrawable(resource: Int, left: Float, top: Float, drawWidth: Float, drawHeight: Float, tint: Int? = null) {
        val drawable = context.getDrawable(resource)?.mutate() ?: return
        if (tint != null) drawable.setTint(tint)
        drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + drawWidth).roundToInt(), (top + drawHeight).roundToInt())
        drawable.draw(canvas)
    }

    private fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float) {
        canvas.drawBitmap(bitmap, null, RectF(left, top, right, bottom), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawBar(left: Float, top: Float, right: Float, bottom: Float, fraction: Double, color: Int) {
        val radius = (bottom - top) / 2f
        roundRect(left, top, right, bottom, radius, strongLine)
        val fillRight = left + (right - left) * fraction.coerceIn(0.0, 1.0).toFloat()
        if (fillRight > left) roundRect(left, top, fillRight.coerceAtLeast(left + bottom - top), bottom, radius, color)
    }

    private fun drawSegmentedBar(left: Float, top: Float, right: Float, bottom: Float, segments: List<Pair<Double, Int>>) {
        drawBar(left, top, right, bottom, 0.0, strongLine)
        val radius = (bottom - top) / 2f
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        var x = left
        segments.forEachIndexed { index, segment ->
            val segmentRight = min(right, x + (right - left) * segment.first.coerceIn(0.0, 1.0).toFloat())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = segment.second }
            if (index == 0) canvas.drawRoundRect(RectF(x, top, segmentRight, bottom), radius, radius, paint)
            else canvas.drawRect(x, top, segmentRight, bottom, paint)
            x = segmentRight
        }
        canvas.restore()
    }

    private fun fitText(value: String, x: Float, baseline: Float, preferred: Float, minimum: Float, color: Int, typeface: Typeface, maxWidth: Float) {
        var size = preferred
        var paint = textPaint(size, color, typeface)
        while (size > minimum && paint.measureText(value) > maxWidth) {
            size -= 1f
            paint = textPaint(size, color, typeface)
        }
        canvas.drawText(ellipsize(value, paint, maxWidth), x, baseline, paint)
    }

    private fun text(value: String, x: Float, baseline: Float, size: Float, color: Int, typeface: Typeface, align: Paint.Align = Paint.Align.LEFT, width: Float = Float.MAX_VALUE) {
        val paint = textPaint(size, color, typeface, align)
        canvas.drawText(ellipsize(value, paint, width), x, baseline, paint)
    }

    private fun textPaint(size: Float, color: Int, typeface: Typeface, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
        textAlign = align
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth == Float.MAX_VALUE || paint.measureText(value) <= maxWidth) return value
        val suffix = "…"
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end--
        return value.substring(0, end) + suffix
    }

    private fun line(startX: Float, startY: Float, endX: Float, endY: Float, color: Int) {
        canvas.drawLine(startX, startY, endX, endY, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1f })
    }

    private fun circle(x: Float, y: Float, radius: Float, color: Int) {
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })
    }
}
