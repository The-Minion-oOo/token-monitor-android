# Widget design

The responsive widget is the dashboard's Home module shrunk to a card. The separate
pages widget extends that language across Overview, Limits, Breakdown, and
Activity pages. Both should read as the same product: same type, palette, labels,
and countdowns.

## Type and color

- Labels, section titles, dates, quota names and the controls use the monospace family
  the dashboard uses for its labels. Section titles and the brand line are bold,
  uppercase and letter-spaced, like `LIMITS` and `TOTAL TOKENS` in the app.
- The figure is the app's display face: sans-serif medium with tabular numerals. It
  auto-sizes down to keep every digit; it is never abbreviated.
- The four pages use one shared scale for section, primary, body, and secondary
  text. Supporting labels use a slightly stronger muted tone so they remain
  readable on a launcher without competing with primary values.
- Every color comes from the dashboard `Palette` at render time. The layouts carry no
  colors of their own beyond placeholders, so Default, Obsidian, Porcelain and custom
  desktop codes all apply, and a theme change redraws saved widgets immediately.
- Quota bars use the app's risk tones: success under 65 percent used, orange to 85,
  red above. Provider marks use the vendor colors the app uses in its lists.
- The card is the shell color under the dashboard's gradient, edged with the palette's
  strong line at its own opacity. On Android 12 and newer the corner radius is the
  launcher's system radius so the card matches its neighbors.

## Drawing without blur

RemoteViews can only show text, images and a few containers, so bars and the chart are
bitmaps. Earlier revisions drew them at a fixed size and let the launcher stretch
them, which is where the soft edges came from. Now the provider asks the launcher for
the exact sizes the widget can take (`OPTION_APPWIDGET_SIZES` on Android 12 and newer,
the min and max options before that), renders one `RemoteViews` per size, and draws
each bitmap at that size in device pixels. The chart's height is whatever the fixed
rows leave over, computed the same way the layout lays them out, and a test asserts the
drawn bitmap matches the view it lands in.

## Content by shape

| Shape | What earns the space |
| --- | --- |
| Compact | Date and the toggle, the figure, cost and messages; the tool bar and tightest window appear as height allows, then a Refresh icon with the saved time. |
| Wide | Brand header with Refresh, status and the toggle; figure, cost and date beside two stats. |
| Portrait | Compact content plus a second provider window. |
| Overview | Brand header, figure with two stats, tool split, one window from 190 dp and a second from 221 dp, and a fixed-height seven-day chart once the launcher gives 301 dp. |
| Detailed | Overview content plus a third stat, every window with its reset in two columns per provider, and the week total on the chart. |

There is no button row. Live is a toggle and Refresh a vector icon, both in the
header and both on 48 dp touch targets. The visible Live track is 40×22 dp; Refresh
has no visible enclosing circle.
Spare height goes to one place per layout instead of into gaps: the figure in Compact,
Wide, Portrait and Overview; the chart in Detailed. What a size shows is decided from
the height the launcher reports, so nothing is clipped at a declared minimum.

## Non-ideal states

Saved and Connecting keep the last figures and their snapshot date. A widget with no
snapshot shows a short message and an `OPEN APP` action; while a Live session is
waiting for its first data the Stop control stays available. Missing quota data is
labeled, and a missing calendar day is a dash rather than a zero bar. Reduced motion
leaves the digits still; otherwise they roll when the total changes.

## Full-size pages

The provider sends one `RemoteViews` card to the launcher and stores the selected
page locally for each widget. It loads the newer of the in-process session snapshot
and private cache, then draws the selected page into a fixed 1.82:1 bitmap. The alpha
bitmap is rendered at the launcher's display density, with a 1200-pixel safety cap,
instead of being enlarged from a fixed low-resolution raster. One page remains well
inside Android's 1.5-screen aggregate widget bitmap budget. The launcher scales the
card as one unit, so it cannot apply collection-card depth, remeasure page rows, or
expose rear cards. Changing pages performs no network work. Pages do not auto-advance
or schedule their own updates.

Each page retains the same header controls. Forty-eight-dp left and right edge targets
cycle the pages and four dots, inset above the lower edge, show the current position; tapping the content opens
the app. Overview prioritizes the total, three operating stats, tool share, and week
summary. Limits shows up to two providers, each with its two tightest windows.
Breakdown compares tools and models and keeps sparse results top-aligned without
shrinking their type. Activity labels its daily seven-day bars and identifies the
heatmap as thirteen weeks.
Missing snapshot, limits, breakdown, or history data produces a named empty state
instead of zero-filled evidence.

Every position on the four pages comes from [the pages widget specification](WIDGET_SPEC.md),
which was measured from the approved concept cards. `WidgetDeckGrid` carries those
numbers in reference units of a 364×200 card, and the renderer scales its canvas
once to the fitted frame. A smaller allocation is therefore the same picture drawn
smaller, down to the launcher's 250×110 dp minimum; there is no separate compact
composition to drift out of step. Bars, the seven-day chart and the heatmap are
drawn straight onto that canvas, never as separate bitmaps.

The pages use a bundled Latin subset of JetBrains Mono for labels and rows, so
the widget looks the same on a Pixel and on a Samsung launcher, which substitutes
its own face for the system monospace family. Figures use the system sans-serif
in bold with tabular numerals. The overview token total is the only text that
shrinks to fit; everything else is ellipsized at its column, and the specification
records the two fallbacks that exist (a shorter Limits title and shorter Activity
captions). The Saved state keeps its off switch but uses a clearer label and knob
contrast than ordinary metadata.

## Verification

`WidgetDesignTest` renders the production RemoteViews from test data in Live,
Saved, Connecting, empty and waiting states across all five shapes and extra height,
and asserts the chart bitmap matches its view size. `WidgetControlsTest` checks the
minimum dimensions at normal and 130 percent text, long counts, 48 dp targets, picker
metadata and the switch between widget polling and dashboard streaming. `WidgetThemeTest` covers the three presets
and a custom code, plus a real AppWidgetHost recolor on theme change.
`WidgetDeckDesignTest` renders all four pages from the dense showcase fixture
(three tools, four models, four quota windows, 65 days of history) at the minimum,
medium and large sizes in dark and light themes, checks provider metadata,
page-state wrapping, identical bitmap dimensions and aspect ratios, accessible
controls, and covers saved, stale, offline, empty, and v0.54-compatible snapshots. The gallery
images come from the same layouts through `tools/capture-showcase.ps1`.

Physical-phone coverage is recorded separately in [Validation](VALIDATION.md).
