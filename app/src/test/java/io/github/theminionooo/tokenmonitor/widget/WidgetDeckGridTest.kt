package io.github.theminionooo.tokenmonitor.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDeckGridTest {
    @Test fun `the card is one fixed grid scaled uniformly to the fitted frame`() {
        assertEquals(1.82f, WidgetDeckGrid.ASPECT, 0.001f)
        assertEquals(1f, WidgetDeckGrid.scale(364f), 0.0001f)
        assertEquals(393f / 364f, WidgetDeckGrid.scale(393f), 0.0001f)
        assertEquals(200f / 364f, WidgetDeckGrid.scale(200f), 0.0001f)
    }

    @Test fun `bitmap scale follows density without exceeding the pixel cap`() {
        assertEquals(3f, WidgetDeckRenderer.bitmapScale(393f, 3f), 0.0001f)
        assertEquals(2.4f, WidgetDeckRenderer.bitmapScale(500f, 3f), 0.0001f)
        assertEquals(0.75f, WidgetDeckRenderer.bitmapScale(1_600f, 1f), 0.0001f)
    }

    @Test fun `bands stay inside the content edges and never overlap`() {
        val header = WidgetDeckGrid.Header
        assertTrue(header.PAGE_BASELINE < WidgetDeckGrid.Overview.STATS_TOP)
        assertTrue(header.TOGGLE_LEFT + header.TOGGLE_WIDTH <= WidgetDeckGrid.RIGHT)
        assertTrue(header.REFRESH_X + header.REFRESH_SIZE < header.STATUS_RIGHT)
        assertTrue(WidgetDeckGrid.Overview.STATS_TOP + 2 * WidgetDeckGrid.Overview.STATS_PITCH + WidgetDeckGrid.Overview.STAT_CAPTION_OFFSET < WidgetDeckGrid.Overview.BAR_TOP)
        assertTrue(WidgetDeckGrid.Overview.WEEK_BASELINE < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Limits.ROW_TOPS.last() + WidgetDeckGrid.Limits.RESET_OFFSET < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Breakdown.TOOL_ROW_TOPS.last() + WidgetDeckGrid.Breakdown.TOOL_BAR_BOTTOM_OFFSET < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Breakdown.MODEL_ROW_TOPS.last() + WidgetDeckGrid.Breakdown.MODEL_BAR_BOTTOM_OFFSET < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Activity.DAY_LABEL_BASELINE < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Activity.STAT_CAPTION_BASELINE < header.DOTS_Y)
    }

    @Test fun `stat rows leave room for a value and its caption`() {
        val overview = WidgetDeckGrid.Overview
        // A 15-unit value plus an 8.5-unit caption need more than 24 units per row.
        assertTrue(overview.STATS_PITCH >= WidgetDeckGrid.Type.STAT + WidgetDeckGrid.Type.CAPTION + 2f)
        assertTrue(overview.STAT_CAPTION_OFFSET - overview.STAT_VALUE_OFFSET >= WidgetDeckGrid.Type.CAPTION)
    }

    @Test fun `breakdown tool values keep a fixed gap from the measured share`() {
        val breakdown = WidgetDeckGrid.Breakdown
        assertEquals(136f, breakdown.toolValueRight(24f), 0.0001f)
        assertEquals(breakdown.TOOL_VALUE_GAP, breakdown.TOOL_RIGHT - 24f - breakdown.toolValueRight(24f), 0.0001f)
    }
}
