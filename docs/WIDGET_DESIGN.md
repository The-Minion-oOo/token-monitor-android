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
bitmap is capped below the RemoteViews transfer limit and scaled as one unit, so
launchers cannot apply collection-card depth, remeasure page rows, or expose rear
cards. Changing pages performs no network work. Pages do not auto-advance or
schedule their own updates.

Each page retains the same header controls. Forty-eight-dp left and right edge targets
cycle the pages and four dots show the current position; tapping the content opens
the app. Overview prioritizes the total, three operating stats, tool share, and week
summary. Limits shows up to four tightest windows. Breakdown compares tools and
models. Activity combines a seven-day chart with a compact recent-history heatmap.
Missing snapshot, limits, breakdown, or history data produces a named empty state
instead of zero-filled evidence.

The four pages share one type scale for section headings, primary values, body
rows, and secondary labels. The overview token total is the only deliberate size
exception. Long names are ellipsized rather than rendered with a smaller font.

## Verification

`WidgetDesignTest` renders the production RemoteViews from test data in Live,
Saved, Connecting, empty and waiting states across all five shapes and extra height,
and asserts the chart bitmap matches its view size. `WidgetControlsTest` checks the
minimum dimensions at normal and 130 percent text, long counts, 48 dp targets, picker
metadata and the switch between widget polling and dashboard streaming. `WidgetThemeTest` covers the three presets
and a custom code, plus a real AppWidgetHost recolor on theme change.
`WidgetDeckDesignTest` renders all four pages at medium and large sizes in dark
and light themes, checks provider metadata, page-state wrapping, identical bitmap
dimensions and aspect ratios, accessible controls, and covers saved, stale, offline,
empty, and v0.54-compatible snapshots. The gallery
images come from the same layouts through `tools/capture-showcase.ps1`.

Physical-phone coverage is recorded separately in [Validation](VALIDATION.md).
