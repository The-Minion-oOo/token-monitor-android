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
        assertTrue(WidgetDeckGrid.Breakdown.ROW_TOPS.last() + WidgetDeckGrid.Breakdown.BAR_BOTTOM_OFFSET < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Activity.DAY_LABEL_BASELINE < header.DOTS_Y)
        assertTrue(WidgetDeckGrid.Activity.STAT_CAPTION_BASELINE < header.DOTS_Y)
    }

    @Test fun `marks are centered on the text block beside them`() {
        val header = WidgetDeckGrid.Header
        val brandCapTop = header.BRAND_BASELINE - WidgetDeckGrid.Type.BRAND * 0.73f
        assertEquals((brandCapTop + header.PAGE_BASELINE) / 2f, header.ICON_Y + header.ICON_SIZE / 2f, 1.5f)
        val limits = WidgetDeckGrid.Limits
        val titleCapTop = limits.TITLE_OFFSET - limits.TITLE_SIZE * 0.73f
        assertEquals((titleCapTop + limits.VALUE_OFFSET) / 2f, limits.MARK_OFFSET + limits.MARK_SIZE / 2f, 1.5f)
    }

    @Test fun `stat rows leave room for a value and its caption`() {
        val overview = WidgetDeckGrid.Overview
        // A 15-unit value plus an 8.5-unit caption need more than 24 units per row.
        assertTrue(overview.STATS_PITCH >= WidgetDeckGrid.Type.STAT + WidgetDeckGrid.Type.CAPTION + 2f)
        assertTrue(overview.STAT_CAPTION_OFFSET - overview.STAT_VALUE_OFFSET >= WidgetDeckGrid.Type.CAPTION)
    }

    @Test fun `breakdown rows keep the detail line clear of the name's descenders and the bar`() {
        val breakdown = WidgetDeckGrid.Breakdown
        // Descenders reach about 2.5 units below the 10-unit name; the 9-unit detail line rises about 6.5 above its baseline.
        assertTrue(breakdown.DETAIL_BASELINE_OFFSET - WidgetDeckGrid.Type.SECONDARY * 0.72f >= breakdown.NAME_BASELINE_OFFSET + 2.5f)
        assertTrue(breakdown.BAR_TOP_OFFSET - breakdown.DETAIL_BASELINE_OFFSET >= 4f)
        // The mark's center sits between the name's cap top (baseline − 7.3) and the detail baseline.
        val markCenter = breakdown.MARK_OFFSET + breakdown.MARK_SIZE / 2f
        assertTrue(markCenter > breakdown.NAME_BASELINE_OFFSET - 7.3f && markCenter < breakdown.DETAIL_BASELINE_OFFSET)
        assertEquals((breakdown.NAME_BASELINE_OFFSET - 7.3f + breakdown.DETAIL_BASELINE_OFFSET) / 2f, markCenter, 1.5f)
        val pitch = breakdown.ROW_TOPS[1] - breakdown.ROW_TOPS[0]
        assertTrue(pitch - breakdown.BAR_BOTTOM_OFFSET >= 6f)
        assertTrue(breakdown.TOOL_RIGHT - breakdown.TOOL_NAME_X >= 100f)
    }

    @Test fun `sparse breakdown gives aligned figures the full column width`() {
        val breakdown = WidgetDeckGrid.Breakdown
        assertTrue(breakdown.SPARSE_VALUE_BASELINE > breakdown.SPARSE_IDENTITY_BASELINE)
        assertTrue(breakdown.SPARSE_CAPTION_BASELINE > breakdown.SPARSE_VALUE_BASELINE)
        assertTrue(breakdown.SPARSE_BAR_TOP > breakdown.SPARSE_CAPTION_BASELINE)
        assertTrue(breakdown.SPARSE_COST_BASELINE < WidgetDeckGrid.Header.DOTS_Y)
    }
}
