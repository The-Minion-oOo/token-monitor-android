package io.github.theminionooo.tokenmonitor.widget

/**
 * The measured card grid from `docs/WIDGET_SPEC.md`, in reference units of a
 * 364×200 card. The renderer scales its canvas by [scale] and draws every page
 * with these numbers. Nothing positions widget content any other way; change
 * the specification first, then this file, then recapture the gallery.
 */
internal object WidgetDeckGrid {
    const val WIDTH = 364f
    const val HEIGHT = 200f
    const val ASPECT = WIDTH / HEIGHT

    const val LEFT = 18f
    const val RIGHT = 346f
    const val RADIUS = 20f
    const val EDGE_STROKE = 1.35f

    fun scale(frameWidth: Float): Float = frameWidth / WIDTH

    object Header {
        const val ICON_X = 18f
        /** Centered on the brand and page lines together, not on the brand alone. */
        const val ICON_Y = 20f
        const val ICON_SIZE = 22f
        const val TEXT_X = 48f
        const val BRAND_BASELINE = 30f
        const val PAGE_BASELINE = 43f
        const val REFRESH_X = 238f
        const val REFRESH_Y = 20f
        const val REFRESH_SIZE = 16f
        const val STATUS_RIGHT = 296f
        const val STATUS_BASELINE = 31f
        const val TOGGLE_LEFT = 306f
        const val TOGGLE_TOP = 16f
        const val TOGGLE_WIDTH = 40f
        const val TOGGLE_HEIGHT = 22f
        const val KNOB_RADIUS = 8f
        const val CHEVRON_LEFT_X = 9f
        const val CHEVRON_RIGHT_X = 355f
        const val CHEVRON_Y = 100f
        const val CHEVRON_HALF_WIDTH = 3f
        const val CHEVRON_HALF_HEIGHT = 5f
        const val DOTS_Y = 190f
        const val DOTS_GAP = 7f
    }

    object Overview {
        const val SECTION_BASELINE = 66f
        const val TOTAL_BASELINE = 104f
        const val TOTAL_RIGHT = 232f
        const val COST_BASELINE = 124f
        const val DIVIDER_X = 243f
        const val DIVIDER_TOP = 54f
        const val DIVIDER_BOTTOM = 128f
        const val STATS_X = 257f
        const val STATS_TOP = 50f
        const val STATS_PITCH = 26f
        const val STAT_VALUE_OFFSET = 12f
        const val STAT_CAPTION_OFFSET = 22f
        const val BAR_TOP = 135f
        const val BAR_BOTTOM = 141f
        const val LEGEND_DOT_Y = 154f
        const val LEGEND_DOT_RADIUS = 3f
        const val LEGEND_DOT_INSET = 4f
        const val LEGEND_TEXT_INSET = 13f
        const val LEGEND_BASELINE = 158f
        const val RULE_Y = 167f
        const val WEEK_BASELINE = 184f
        const val WEEK_VALUE_X = 88f
    }

    object Limits {
        val ROW_TOPS = floatArrayOf(52f, 122f)
        const val RULE_Y = 117.5f
        const val MARK_SIZE = 22f
        /** Centered on the title and percentage lines together. */
        const val MARK_OFFSET = 10f
        const val LEFT_TEXT_X = 46f
        const val LEFT_RIGHT_EDGE = 162f
        const val RIGHT_TEXT_X = 197f
        const val TITLE_OFFSET = 14f
        const val TITLE_SIZE = 10f
        const val VALUE_OFFSET = 36f
        const val LEFT_LABEL_SIZE = 13f
        const val BAR_TOP_OFFSET = 42f
        const val BAR_BOTTOM_OFFSET = 46.5f
        const val RESET_OFFSET = 57f
    }

    object Breakdown {
        const val DIVIDER_X = 181f
        const val DIVIDER_TOP = 54f
        const val DIVIDER_BOTTOM = 186f
        const val SECTION_BASELINE = 60f
        const val MODELS_X = 192f
        const val SPARSE_MARK_TOP = 72f
        const val SPARSE_MARK_SIZE = 18f
        const val SPARSE_TOOL_NAME_X = 42f
        const val SPARSE_MODEL_NAME_X = 216f
        const val SPARSE_IDENTITY_BASELINE = 87f
        const val SPARSE_VALUE_BASELINE = 118f
        const val SPARSE_CAPTION_BASELINE = 132f
        const val SPARSE_BAR_TOP = 142f
        const val SPARSE_BAR_BOTTOM = 148f
        const val SPARSE_COST_BASELINE = 163f
        /** Both columns share one row rhythm so rows line up across the divider. */
        val ROW_TOPS = floatArrayOf(68f, 108f, 148f)
        const val MARK_SIZE = 16f
        const val TOOL_NAME_X = 40f
        const val TOOL_RIGHT = 168f
        const val MODEL_NAME_X = 214f
        /** The mark is centered on the two text lines, not on the name alone. */
        const val MARK_OFFSET = 4f
        const val NAME_BASELINE_OFFSET = 10f
        const val DETAIL_BASELINE_OFFSET = 24f
        const val SHARE_GAP = 8f
        const val BAR_TOP_OFFSET = 29f
        const val BAR_BOTTOM_OFFSET = 33f
    }

    object Activity {
        const val DIVIDER_X = 214f
        const val DIVIDER_TOP = 54f
        const val DIVIDER_BOTTOM = 186f
        const val SECTION_BASELINE = 60f
        const val SUMMARY_BASELINE = 79f
        const val LEFT_RIGHT_EDGE = 204f
        const val PLOT_LEFT = 38f
        const val PLOT_TOP = 92f
        const val PLOT_BOTTOM = 168f
        const val BAR_FRACTION = 0.56f
        const val BAR_RADIUS = 2f
        const val DAY_LABEL_BASELINE = 179f
        const val RIGHT_X = 226f
        const val HEAT_TOP = 68f
        const val HEAT_GAP = 2f
        const val HEAT_COLUMNS = 13
        const val HEAT_ROWS = 7
        const val HEAT_RADIUS = 1.5f
        const val MONTH_BASELINE = 144f
        const val RULE_Y = 152f
        const val STAT_VALUE_BASELINE = 172f
        const val STAT_CAPTION_BASELINE = 184f
        const val STAT_DIVIDER_X = 286f
        const val STAT_DIVIDER_TOP = 158f
        const val STAT_DIVIDER_BOTTOM = 186f
        const val STAT_RIGHT_X = 292f
    }

    /** Type sizes in reference units; see the type table in the specification. */
    object Type {
        const val BRAND = 13.5f
        const val PAGE = 9f
        const val SECTION = 10.5f
        const val STATUS = 10.5f
        const val DISPLAY = 44f
        const val DISPLAY_MIN = 26f
        const val FIGURE = 20f
        const val STAT = 13f
        const val BODY = 10f
        const val BODY_STRONG = 10f
        const val LEGEND = 9.5f
        const val SECONDARY = 9f
        const val CAPTION = 7.5f
        const val AXIS = 7f
    }
}
