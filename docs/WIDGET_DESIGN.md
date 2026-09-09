# Widget design

The widget is the dashboard's Home module shrunk to a card. It should read as the
same product: same type, same palette, same labels, same countdowns. Resizing changes
which sections fit, not how they are styled.

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

There is no button row. Live is a toggle and Refresh a round icon, both in the header,
both on 48 dp touch targets although the visible track is 40×22 dp and the ring 30 dp.
Spare height goes to one place per layout instead of into gaps: the figure in Compact,
Wide, Portrait and Overview; the chart in Detailed. What a size shows is decided from
the height the launcher reports, so nothing is clipped at a declared minimum.

## Non-ideal states

Saved and Connecting keep the last figures and their snapshot date. A widget with no
snapshot shows a short message and an `OPEN APP` action; while a Live session is
waiting for its first data the Stop control stays available. Missing quota data is
labeled, and a missing calendar day is a dash rather than a zero bar. Reduced motion
leaves the digits still; otherwise they roll when the total changes.

## Verification

`WidgetDesignTest` renders the production RemoteViews from test data in Live,
Saved, Connecting, empty and waiting states across all five shapes and extra height,
and asserts the chart bitmap matches its view size. `WidgetControlsTest` checks the
minimum dimensions at normal and 130 percent text, long counts, 48 dp targets, picker
metadata and the switch between widget polling and dashboard streaming. `WidgetThemeTest` covers the three presets
and a custom code, plus a real AppWidgetHost recolor on theme change. The gallery
images come from the same layouts through `tools/capture-showcase.ps1`.

Physical-phone coverage is recorded separately in [Validation](VALIDATION.md).
