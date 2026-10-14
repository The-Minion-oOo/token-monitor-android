package io.github.theminionooo.tokenmonitor.ui

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.theminionooo.tokenmonitor.data.storage.TokenNumberAnimation
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Desktop v0.70's "Digit roll" (src/electron/tokenNumberAnimation.js), its port of native numericText.
 * The unchanged leading glyphs stay sharp and follow the layout. Every later glyph leaves where it
 * stood while its replacement rolls in where it lands, and one left-to-right wave crosses both
 * readings. A change arriving mid-roll keeps every visible layer in place, each with its own clock.
 */

internal val LocalTokenNumberAnimation = staticCompositionLocalOf { TokenNumberAnimation.Roll }

/** The dashboard period on screen; a change replaces every reading at once and sweeps instead of ticking. */
internal val LocalTokenRollPeriod = staticCompositionLocalOf<Any?> { null }

/** Every glyph runs a 650 ms envelope; kept glyphs follow the layout over 250 ms. */
internal const val ROLL_GLYPH_MS = 650.0
internal const val ROLL_LAYOUT_MS = 250.0
private const val ROLL_GLYPH_SECONDS = ROLL_GLYPH_MS / 1000.0

/** Live updates cross the changing span in 150 ms; a period switch sweeps at 32 ms per em, at most 250 ms. */
private const val ROLL_WAVE_MS = 150.0
private const val ROLL_PERIOD_PACE_MS = 32.0
private const val ROLL_PERIOD_WAVE_MS = 250.0

/** A period retarget keeps at most two glyphs per place and drops the extras once three-quarters faded. */
private const val ROLL_RETARGET_LAYERS = 2
private const val ROLL_RETARGET_FLOOR = 0.25f

private val RollLayoutEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** A damped second-order step response; the desktop samples one per channel. */
internal fun rollResponse(seconds: Double, damping: Double, period: Double): Double {
    val w = 2 * PI / period
    if (damping == 1.0) return 1 - (1 + w * seconds) * exp(-w * seconds)
    val wd = w * sqrt(1 - damping * damping)
    return 1 - exp(-damping * w * seconds) * (cos(wd * seconds) + damping * w / wd * sin(wd * seconds))
}

/** The four channels at [seconds] into a glyph's envelope; all of them land exactly on 1 at its end. */
internal data class RollChannels(val position: Double, val body: Double, val blur: Double, val smear: Double)

internal fun rollChannels(seconds: Double): RollChannels {
    if (seconds >= ROLL_GLYPH_SECONDS) return RollChannels(1.0, 1.0, 1.0, 1.0)
    val t = max(0.0, seconds)
    return RollChannels(
        position = rollResponse(t, 0.55, 0.353),
        body = rollResponse(t, 1.0, 0.278),
        blur = min(1.0, rollResponse(t, 0.91, 0.398)),
        smear = min(1.0, rollResponse(t, 1.0, 0.22)),
    )
}

/**
 * Formatted character positions, as native numericText matches them. Digits and separators are keyed
 * by index and never roll into each other. A trailing unit (191.8M) is keyed from the end, so it stays
 * sharp and follows the layout when the digits before it change.
 */
internal data class RollPart(val char: String, val numeric: Boolean, val key: String)

internal fun rollParts(text: String, whole: Boolean = false): List<RollPart> {
    if (whole) return listOf(RollPart(text, numeric = false, key = "reading:0"))
    val chars = text.map(Char::toString)
    var unit = chars.size
    while (unit > 0 && !chars[unit - 1][0].isAsciiDigit()) unit--
    if (unit == 0) unit = chars.size
    return chars.mapIndexed { index, char ->
        val numeric = char[0].isAsciiDigit()
        when {
            index >= unit -> RollPart(char, numeric, "unit:${chars.size - index}:$char")
            numeric -> RollPart(char, true, "digit:$index")
            else -> RollPart(char, false, "symbol:$index:$char")
        }
    }
}

private fun Char.isAsciiDigit() = this in '0'..'9'

internal fun rollUnchangedPrefix(before: List<RollPart>, after: List<RollPart>): Int {
    var prefix = 0
    while (prefix < min(before.size, after.size) && before[prefix].char == after[prefix].char) prefix++
    return prefix
}

private fun String.placeIndex(): Int = split(':').getOrNull(1)?.toIntOrNull() ?: 0
private fun String.isUnitKey() = startsWith("unit:")

/**
 * One visible glyph. [x] is its left edge measured from the reading's anchor: the left edge, or the
 * right edge for an end-aligned reading, so outgoing glyphs stay where they stood as the width changes.
 */
internal data class RollGlyph(
    val key: String,
    val char: String,
    val numeric: Boolean,
    val x: Float,
    val width: Float,
    val y: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    /** The desktop's blur in pixels. It is tracked so retargets match the desktop's envelope but is not drawn. */
    val blur: Float = 0f,
    /** When an outgoing glyph started leaving, in milliseconds on the roll clock. */
    val departedAt: Double? = null,
    val exitDirection: Float? = null,
) {
    val center: Float get() = x + width / 2f
}

internal enum class RollLayerKind { Incoming, Kept, Outgoing }

/** A glyph's motion within one roll, read against the roll's start on the same millisecond clock. */
internal data class RollLayer(
    val kind: RollLayerKind,
    val key: String,
    val char: String,
    val numeric: Boolean,
    val fromX: Float,
    val toX: Float,
    val width: Float,
    val baseY: Float = 0f,
    val baseScale: Float = 1f,
    val baseAlpha: Float = 1f,
    val baseBlur: Float = 0f,
    val direction: Float = 1f,
    val delayMs: Double = 0.0,
    /** Seconds this glyph's envelope had already run when the roll began. */
    val age: Double = 0.0,
    val fade: Boolean = false,
) {
    val endMs: Double get() = when (kind) {
        RollLayerKind.Kept -> max(ROLL_LAYOUT_MS, if (fade) ROLL_GLYPH_MS else 0.0)
        else -> delayMs + (ROLL_GLYPH_SECONDS - age) * 1000.0
    }
}

/** How a glyph looks [elapsedMs] into its roll; [travel] and [blurRoom] are ink-scaled distances in pixels. */
internal fun RollLayer.glyphAt(elapsedMs: Double, travel: Float, blurRoom: Float): RollGlyph {
    val departedAt = if (kind == RollLayerKind.Outgoing) delayMs - age * 1000.0 else null
    return when (kind) {
        RollLayerKind.Kept -> {
            val move = RollLayoutEasing.transform((elapsedMs / ROLL_LAYOUT_MS).coerceIn(0.0, 1.0).toFloat())
            val channels = rollChannels(if (fade) elapsedMs / 1000.0 else ROLL_GLYPH_SECONDS)
            RollGlyph(
                key, char, numeric,
                x = fromX + (toX - fromX) * move, width = width,
                y = baseY * (1f - move),
                scale = baseScale + (1f - baseScale) * move,
                alpha = (baseAlpha + (1f - baseAlpha) * channels.body).toFloat(),
                blur = (baseBlur * (1.0 - channels.blur)).toFloat(),
            )
        }
        RollLayerKind.Incoming -> {
            val channels = rollChannels((elapsedMs - delayMs) / 1000.0)
            RollGlyph(
                key, char, numeric, x = toX, width = width,
                y = (direction * travel * (1.0 - channels.position)).toFloat(),
                scale = if (numeric) (0.3984375 + 0.6015625 * channels.body).toFloat() else 1f,
                alpha = channels.body.toFloat(),
                blur = (blurRoom * (1.0 - channels.blur)).toFloat(),
            )
        }
        RollLayerKind.Outgoing -> {
            val start = rollChannels(age)
            val channels = rollChannels(age + max(0.0, elapsedMs - delayMs) / 1000.0)
            val initialSmear = min(0.999999, start.smear)
            val progress = ((channels.smear - initialSmear) / (1 - initialSmear)).coerceIn(0.0, 1.0)
            val fromBlur = baseBlur * (1 - progress)
            // Distances are in ems of the glyph as captured, so one caught mid-entry leaves a shorter way.
            val toBlur = blurRoom * baseScale * progress
            RollGlyph(
                key, char, numeric, x = fromX, width = width,
                y = (baseY - direction * travel * baseScale * (channels.position - start.position)).toFloat(),
                scale = baseScale * if (numeric) ((1 - 0.6015625 * channels.body) / (1 - 0.6015625 * start.body)).toFloat() else 1f,
                alpha = (baseAlpha * (1 - channels.body) / (1 - start.body)).toFloat().coerceIn(0f, 1f),
                // Two stacked Gaussian blurs combine as the root of their summed squares.
                blur = sqrt(fromBlur * fromBlur + toBlur * toBlur).toFloat(),
                departedAt = departedAt,
                exitDirection = direction,
            )
        }
    }
}

/**
 * Plans a roll from what is on screen now ([previous], already positioned against the anchor) to [after].
 * [interrupted] means an earlier roll was still running; [nowMs] is the shared roll clock.
 */
internal fun planRoll(
    fromText: String,
    toText: String,
    previous: List<RollGlyph>,
    after: List<RollGlyph>,
    increasing: Boolean,
    period: Boolean,
    whole: Boolean,
    interrupted: Boolean,
    fontPx: Float,
    nowMs: Double,
): List<RollLayer> {
    val direction = if (increasing) 1f else -1f
    // Rapid period retargets would stack every earlier reading's leaving glyphs. Keep the strongest few.
    val visible = if (period && previous.isNotEmpty()) {
        previous.groupBy { it.key }.values.flatMap { glyphs ->
            glyphs.sortedByDescending { it.alpha }.take(ROLL_RETARGET_LAYERS)
                .filterIndexed { index, glyph -> index == 0 || glyph.alpha >= ROLL_RETARGET_FLOOR }
        }
    } else previous
    val unchanged = rollUnchangedPrefix(rollParts(fromText, whole), rollParts(toText, whole))
    val changing = { key: String -> !key.isUnitKey() && key.placeIndex() >= unchanged }
    // The wave is spatial, so a leaving glyph and the one landing over it change together.
    val centers = visible.filter { changing(it.key) }.map { it.center } + after.filter { changing(it.key) }.map { it.center }
    val waveStart = centers.minOrNull() ?: 0f
    val waveSpan = (centers.maxOrNull() ?: 0f) - waveStart
    val delayAt = { center: Float ->
        val distance = max(0f, center - waveStart).toDouble()
        when {
            whole -> 0.0
            period -> min(ROLL_PERIOD_WAVE_MS, ROLL_PERIOD_PACE_MS * distance / fontPx.coerceAtLeast(1f))
            interrupted || waveSpan <= 0f -> 0.0
            else -> ROLL_WAVE_MS * min(1.0, distance / waveSpan)
        }
    }
    val layers = mutableListOf<RollLayer>()
    fun leave(old: RollGlyph) {
        val scheduled = nowMs + delayAt(old.center)
        // A period sweep may pull a glyph still waiting on an earlier wave forward; ones already leaving keep their clocks.
        val departedAt = if (period) min(old.departedAt ?: Double.POSITIVE_INFINITY, scheduled) else old.departedAt ?: scheduled
        val age = ((nowMs - departedAt) / 1000.0).coerceIn(0.0, 0.649)
        layers += RollLayer(
            RollLayerKind.Outgoing, old.key, old.char, old.numeric, old.x, old.x, old.width,
            baseY = old.y, baseScale = old.scale, baseAlpha = old.alpha, baseBlur = old.blur,
            direction = old.exitDirection ?: direction, delayMs = max(0.0, departedAt - nowMs), age = age,
        )
    }
    after.forEachIndexed { index, glyph ->
        val candidates = visible.filter { it.key == glyph.key }
        val matching = if (index < unchanged || glyph.key.isUnitKey()) {
            candidates.filter { it.char == glyph.char }.maxByOrNull { it.alpha }
        } else null
        layers += if (matching != null) {
            RollLayer(
                RollLayerKind.Kept, glyph.key, glyph.char, glyph.numeric, matching.x, glyph.x, glyph.width,
                baseY = matching.y, baseScale = matching.scale, baseAlpha = matching.alpha, baseBlur = matching.blur,
                fade = matching.alpha < 0.999f || matching.blur > 0f,
            )
        } else {
            RollLayer(
                RollLayerKind.Incoming, glyph.key, glyph.char, glyph.numeric, glyph.x, glyph.x, glyph.width,
                direction = direction, delayMs = delayAt(glyph.center),
            )
        }
        candidates.filter { it !== matching }.forEach(::leave)
    }
    val keys = after.map { it.key }.toSet()
    visible.filter { it.key !in keys }.forEach(::leave)
    return layers
}


private class DigitRollState(text: String, value: Long, period: Any?) {
    var settledText by mutableStateOf(text)
    var layers by mutableStateOf(emptyList<RollLayer>())
    var anchorWidth by mutableIntStateOf(0)
    var startNanos = 0L
    val frameNanos = mutableLongStateOf(0L)
    var value = value
    var period = period
    /** The widest reading the parent allows; a reading that would not fit settles straight to its ellipsis. */
    var availableWidth = Int.MAX_VALUE

    fun elapsedMs(): Double = (frameNanos.longValue - startNanos) / 1_000_000.0

    suspend fun runToEnd() {
        val end = layers.maxOfOrNull { it.endMs } ?: 0.0
        while (elapsedMs() < end) {
            withFrameNanos { frameNanos.longValue = it }
        }
        layers = emptyList()
    }
}

/** A breakdown row's token figure: end-aligned, fading before the cost line beneath it. */
@Composable
internal fun RowTokenValue(tokens: Long) = RollingTokenText(
    value = tokens,
    text = formatTokens(tokens),
    style = MaterialTheme.typography.bodySmall,
    color = Ink,
    alignEnd = true,
    wholeOnPeriod = true,
    fadeTop = 4.dp,
    fadeBottom = 2.dp,
)

/**
 * A token reading that rolls like the desktop when it changes. With Count up chosen or motion reduced it
 * is plain text. [alignEnd] anchors the reading at its right edge, as in breakdown rows; [wholeOnPeriod]
 * rolls the whole reading as one unit on a period switch, which keeps long lists light.
 * [fadeTop] and [fadeBottom] are the room above and below the line box where rolling glyphs fade out.
 */
@Composable
internal fun RollingTokenText(
    value: Long,
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    wholeOnPeriod: Boolean = false,
    fadeTop: Dp = 3.dp,
    fadeBottom: Dp = 3.dp,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val rolling = LocalInteractionMotion.current && LocalTokenNumberAnimation.current == TokenNumberAnimation.Roll
    val period = LocalTokenRollPeriod.current
    val state = remember { DigitRollState(text, value, period) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val textStyle = remember(style, color) { style.merge(TextStyle(color = color)) }
    val fontPx = with(density) { textStyle.fontSize.toPx() }
    // Travel is measured in ink height, not the line box, as on the desktop.
    val ink = remember(textStyle.fontFamily, textStyle.fontWeight, resolver) {
        val typeface = runCatching {
            resolver.resolve(textStyle.fontFamily, textStyle.fontWeight ?: FontWeight.Normal).value as? Typeface
        }.getOrNull()
        val bounds = Rect()
        Paint().apply { this.typeface = typeface; textSize = 100f }.getTextBounds("0", 0, 1, bounds)
        if (bounds.height() > 0) bounds.height() / 100f else 0.72f
    }
    val travel = 0.59375f * ink * fontPx
    val blurRoom = 0.16f * ink * fontPx
    val currentRolling by rememberUpdatedState(rolling)

    // Keyed on the period too, so a period switch that leaves this reading unchanged is still recorded.
    LaunchedEffect(text, period) {
        val fromText = state.settledText
        val wasRolling = state.layers.isNotEmpty()
        if (!currentRolling || fromText == text) {
            state.value = value
            state.period = period
            if (!currentRolling) state.layers = emptyList()
            state.settledText = text
            // An unchanged reading never restarts; a roll already in flight finishes where it is.
            if (wasRolling && currentRolling) state.runToEnd()
            return@LaunchedEffect
        }
        val now = withFrameNanos { it }
        // Committed after the frame, so a change that lands in the same frame still compares with what was shown.
        val periodChanged = state.period != period
        val increasing = value > state.value
        state.value = value
        state.period = period
        val whole = periodChanged && wholeOnPeriod
        fun positioned(reading: String): Pair<List<RollGlyph>, Int> {
            val layout = measurer.measure(AnnotatedString(reading), textStyle, softWrap = false, maxLines = 1)
            val width = layout.size.width
            val anchor = if (alignEnd) width.toFloat() else 0f
            val glyphs = rollParts(reading, whole).let { parts ->
                if (whole) listOf(RollGlyph(parts[0].key, parts[0].char, false, -anchor, width.toFloat()))
                else parts.mapIndexed { index, part ->
                    val left = layout.getHorizontalPosition(index, true)
                    val right = if (index + 1 < reading.length) layout.getHorizontalPosition(index + 1, true) else width.toFloat()
                    RollGlyph(part.key, part.char, part.numeric, left - anchor, right - left)
                }
            }
            return glyphs to width
        }
        val (after, width) = positioned(text)
        val previous = if (wasRolling) {
            val elapsed = (now - state.startNanos) / 1_000_000.0
            state.layers.map { it.glyphAt(elapsed, travel, blurRoom) }
                .filter { it.alpha > 0.001f }
                // Departure times are kept on a clock that starts with each roll, so shift them to the new start.
                .map { glyph -> glyph.departedAt?.let { glyph.copy(departedAt = it - elapsed) } ?: glyph }
        } else positioned(fromText).let { (glyphs, fromWidth) -> if (fromWidth > state.availableWidth) null else glyphs }
        // A reading wider than its space is ellipsized when settled; it lands there directly instead of rolling.
        if (previous == null || width > state.availableWidth) {
            state.layers = emptyList()
            state.settledText = text
            return@LaunchedEffect
        }
        state.layers = planRoll(fromText, text, previous, after, increasing, periodChanged, whole, wasRolling, fontPx, 0.0)
        state.anchorWidth = width
        state.startNanos = now
        state.frameNanos.longValue = now
        state.settledText = text
        state.runToEnd()
    }

    val shown = if (rolling) state.settledText else text
    val layers = state.layers
    // One stable node for the reading, so TalkBack keeps its place while glyphs come and go.
    Box(
        modifier
            .clearAndSetSemantics { this.text = AnnotatedString(shown) }
            .layout { measurable, constraints ->
                state.availableWidth = constraints.maxWidth
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    ) {
        if (!rolling || layers.isEmpty()) {
            // Settled readings are ordinary text, so nothing about the steady layout changes.
            Text(shown, style = textStyle, maxLines = 1, softWrap = false, overflow = overflow)
        } else {
            RollingGlyphs(layers, state, alignEnd, textStyle, fontPx, travel, blurRoom, fadeTop, fadeBottom)
        }
    }
}

@Composable
private fun RollingGlyphs(
    layers: List<RollLayer>,
    state: DigitRollState,
    alignEnd: Boolean,
    textStyle: TextStyle,
    fontPx: Float,
    travel: Float,
    blurRoom: Float,
    fadeTop: Dp,
    fadeBottom: Dp,
) {
    val density = LocalDensity.current
    // Whole pixels throughout, so the reported size is exactly the settled text's and nothing shifts.
    val roomTop = with(density) { fadeTop.toPx() }.let(::ceil).toInt()
    val roomBottom = with(density) { fadeBottom.toPx() }.let(::ceil).toInt()
    val anchorWidth = state.anchorWidth
    val anchor = if (alignEnd) anchorWidth.toFloat() else 0f
    // Sideways the mask stays opaque: a little room plus whatever the outgoing reading overhangs.
    val overhangLeft = max(0f, -(layers.minOf { min(it.fromX, it.toX) } + anchor))
    val overhangRight = max(0f, layers.maxOf { max(it.fromX, it.toX) + it.width } + anchor - anchorWidth)
    val roomLeft = ceil(0.25f * fontPx + overhangLeft).toInt()
    val roomRight = ceil(0.25f * fontPx + overhangRight).toInt()
    Layout(
        content = {
            layers.forEach { layer ->
                BasicText(
                    layer.char,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    // Position, scale and fade follow the desktop envelope. Its blur is not drawn: a blur
                    // effect on every glyph crashed the Android emulator's renderer in testing.
                    modifier = Modifier.graphicsLayer {
                        val glyph = layer.glyphAt(state.elapsedMs(), travel, blurRoom)
                        translationX = roomLeft + anchor + glyph.x
                        translationY = roomTop + glyph.y
                        scaleX = glyph.scale
                        scaleY = glyph.scale
                        alpha = glyph.alpha
                    },
                )
            }
        },
        modifier = Modifier
            .layout { measurable, constraints ->
                // Padding cancelled by the reported size: the mask gets room to fade into without moving layout.
                val placeable = measurable.measure(Constraints())
                val width = (placeable.width - roomLeft - roomRight).coerceIn(constraints.minWidth, constraints.maxWidth)
                val height = (placeable.height - roomTop - roomBottom).coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(width, height) { placeable.place(-roomLeft, -roomTop) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val height = size.height
                if (height > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            (roomTop / height) to Color.Black,
                            ((height - roomBottom) / height) to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val lineHeight = placeables.maxOfOrNull { it.height } ?: 0
        layout(anchorWidth + roomLeft + roomRight, lineHeight + roomTop + roomBottom) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}
