package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitRollTest {
    private val travel = 10f
    private val blurRoom = 2f

    /** Every glyph 10 px wide from x = 0, as a left-anchored reading lays them out. */
    private fun glyphs(text: String, whole: Boolean = false) = rollParts(text, whole).mapIndexed { index, part ->
        RollGlyph(part.key, part.char, part.numeric, x = index * 10f, width = if (whole) text.length * 10f else 10f)
    }

    private fun plan(
        from: String,
        to: String,
        previous: List<RollGlyph> = glyphs(from),
        increasing: Boolean = true,
        period: Boolean = false,
        whole: Boolean = false,
        interrupted: Boolean = false,
    ) = planRoll(from, to, previous, glyphs(to, whole), increasing, period, whole, interrupted, fontPx = 10f, nowMs = 0.0)

    @Test fun `digits and separators are keyed by place and a trailing unit from the end`() {
        assertEquals(listOf("digit:0", "symbol:1:,", "digit:2", "digit:3", "digit:4"), rollParts("1,234").map { it.key })
        assertEquals(listOf("digit:0", "digit:1", "digit:2", "symbol:3:.", "digit:4", "unit:1:M"), rollParts("191.8M").map { it.key })
        assertEquals("unit:1:%", rollParts("43%").last().key)
        assertEquals(listOf("reading:0"), rollParts("1,234", whole = true).map { it.key })
        assertEquals(3, rollUnchangedPrefix(rollParts("1,234"), rollParts("1,240")))
        assertEquals(0, rollUnchangedPrefix(rollParts("999"), rollParts("1,000")))
    }

    @Test fun `channels start at rest, the position springs past its target and every channel lands on one`() {
        val start = rollChannels(0.0)
        assertEquals(0.0, start.position, 1e-9)
        assertEquals(0.0, start.body, 1e-9)
        assertTrue((1..64).map { rollChannels(it / 100.0).position }.any { it > 1.0 })
        assertEquals(RollChannels(1.0, 1.0, 1.0, 1.0), rollChannels(ROLL_GLYPH_MS / 1000.0))
    }

    @Test fun `a live tick keeps the unchanged prefix and sweeps the changed digits in 150 ms`() {
        val layers = plan("1,234", "1,240")
        assertEquals(3, layers.count { it.kind == RollLayerKind.Kept })
        val incoming = layers.filter { it.kind == RollLayerKind.Incoming }
        val outgoing = layers.filter { it.kind == RollLayerKind.Outgoing }
        assertEquals(listOf("4", "0"), incoming.map { it.char })
        assertEquals(listOf("3", "4"), outgoing.map { it.char })
        assertEquals(listOf(0.0, 150.0), incoming.map { it.delayMs })
        assertEquals(listOf(0.0, 150.0), outgoing.map { it.delayMs })
        assertEquals(800.0, layers.maxOf { it.endMs }, 1e-9)
    }

    @Test fun `regrouping rolls every place, interrupted ticks start at once and period switches keep their pace`() {
        assertEquals(0, plan("999", "1,000").count { it.kind == RollLayerKind.Kept })
        assertTrue(plan("1,234", "1,240", interrupted = true).all { it.delayMs == 0.0 })
        // 32 ms per em: the last changed digit is one em (10 px) past the first.
        assertEquals(32.0, plan("1,234", "1,240", period = true).filter { it.kind == RollLayerKind.Incoming }.maxOf { it.delayMs }, 1e-9)
        val whole = plan("1,234", "5,678", previous = glyphs("1,234", whole = true), period = true, whole = true)
        assertEquals(listOf(RollLayerKind.Incoming, RollLayerKind.Outgoing), whole.map { it.kind })
        assertTrue(whole.all { it.delayMs == 0.0 && !it.numeric })
    }

    @Test fun `increases enter from below and leave upward, decreases the other way`() {
        val up = plan("1,234", "1,240").first { it.kind == RollLayerKind.Incoming }
        val down = plan("1,240", "1,234", increasing = false).first { it.kind == RollLayerKind.Incoming }
        assertTrue(up.glyphAt(0.0, travel, blurRoom).y > 0f)
        assertTrue(down.glyphAt(0.0, travel, blurRoom).y < 0f)
        val leaving = plan("1,234", "1,240").first { it.kind == RollLayerKind.Outgoing }
        assertTrue(leaving.glyphAt(300.0, travel, blurRoom).y < 0f)
    }

    @Test fun `an arriving glyph is hidden and blurred until it lands sharp and full size`() {
        val incoming = plan("1,234", "1,240").first { it.kind == RollLayerKind.Incoming }
        val first = incoming.glyphAt(0.0, travel, blurRoom)
        assertEquals(0f, first.alpha, 1e-6f)
        assertEquals(0.3984375f, first.scale, 1e-6f)
        assertEquals(blurRoom, first.blur, 1e-6f)
        val landed = incoming.glyphAt(incoming.endMs, travel, blurRoom)
        assertEquals(1f, landed.alpha, 1e-6f)
        assertEquals(0f, landed.y, 1e-6f)
        assertEquals(1f, landed.scale, 1e-6f)
        assertEquals(0f, landed.blur, 1e-6f)
        val gone = plan("1,234", "1,240").last { it.kind == RollLayerKind.Outgoing }
        assertEquals(1f, gone.glyphAt(0.0, travel, blurRoom).alpha, 1e-6f)
        assertEquals(0f, gone.glyphAt(gone.endMs, travel, blurRoom).alpha, 1e-6f)
    }

    @Test fun `a kept glyph follows the layout when the width changes`() {
        val previous = glyphs("1,234").map { it.copy(x = it.x - 50f) }
        val next = glyphs("12,340").map { it.copy(x = it.x - 60f) }
        val kept = planRoll("1,234", "12,340", previous, next, true, false, false, false, 10f, 0.0)
            .single { it.kind == RollLayerKind.Kept }
        assertEquals(-50f, kept.glyphAt(0.0, travel, blurRoom).x, 1e-6f)
        assertEquals(-60f, kept.glyphAt(ROLL_LAYOUT_MS, travel, blurRoom).x, 1e-6f)
    }

    @Test fun `a retarget continues every visible glyph from where it is`() {
        val first = plan("1,234", "1,240")
        val elapsed = 200.0
        val visible = first.map { it.glyphAt(elapsed, travel, blurRoom) }.filter { it.alpha > 0.001f }
            .map { glyph -> glyph.departedAt?.let { glyph.copy(departedAt = it - elapsed) } ?: glyph }
        val second = plan("1,240", "1,250", previous = visible, interrupted = true)
        val leaving = second.filter { it.kind == RollLayerKind.Outgoing }
        leaving.forEach { layer ->
            val before = visible.first { it.key == layer.key && it.char == layer.char }
            val now = layer.glyphAt(0.0, travel, blurRoom)
            assertEquals(before.alpha, now.alpha, 1e-4f)
            assertEquals(before.y, now.y, 1e-4f)
        }
        // The digit that was already leaving keeps its own clock instead of starting over.
        assertTrue(leaving.any { it.age > 0.0 })
    }

    @Test fun `a glyph caught mid-entry leaves in its own smaller ems`() {
        val full = RollGlyph("digit:0", "1", true, 0f, 10f)
        val caught = full.copy(scale = 0.5f)
        val leaveFull = plan("1", "2", previous = listOf(full)).single { it.kind == RollLayerKind.Outgoing }
        val leaveCaught = plan("1", "2", previous = listOf(caught)).single { it.kind == RollLayerKind.Outgoing }
        assertEquals(leaveFull.glyphAt(300.0, travel, blurRoom).y / 2, leaveCaught.glyphAt(300.0, travel, blurRoom).y, 1e-4f)
    }

    @Test fun `the edge fade shows a resting glyph fully and hides one that has rolled past the room`() {
        // A 40 px line box with ink from 10 to 32, and 6 px of fade room above and 4 below.
        val coverage = { y: Float, scale: Float -> rollMaskCoverage(6f, 4f, 40f, 10f, 32f, y, scale) }
        assertEquals(1f, coverage(0f, 1f), 1e-6f)
        assertEquals(1f, coverage(-10f, 1f), 1e-6f)
        assertEquals(0f, coverage(-40f, 1f), 1e-6f)
        assertEquals(0f, coverage(40f, 1f), 1e-6f)
        val partly = coverage(-14f, 1f)
        assertTrue(partly > 0f && partly < 1f)
        // Shrinking about the centre pulls the ink back inside the line box.
        assertTrue(coverage(-14f, 0.4f) > partly)
    }

    @Test fun `a period retarget keeps only the strongest two glyphs per place`() {
        val crowded = listOf(1f, 0.6f, 0.2f, 0.1f).map { RollGlyph("digit:0", "1", true, 0f, 10f, alpha = it) }
        val layers = plan("1", "2", previous = crowded, period = true)
        assertEquals(listOf(1f, 0.6f), layers.filter { it.kind == RollLayerKind.Outgoing }.map { it.baseAlpha })
    }
}
