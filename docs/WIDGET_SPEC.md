# Pages widget specification

This is the measured contract for the four-page widget (Overview, Limits,
Breakdown, Activity). The renderer implements these numbers; nothing in the
renderer positions content by any other means. When the design needs to change,
change this file first, then make the renderer match it, then recapture the
gallery images. Do not nudge coordinates in code without updating this page.

The reference images are the four concept cards approved on September 21, 2026.
They were measured at 1536×1024 pixels; the card in each is 1382 pixels wide, so
one reference unit below is 1382 / 364 ≈ 3.8 image pixels.

## Card and coordinate system

- The card is always 1.82:1. The launcher allocation is fitted to that ratio and
  the card is centered in it.
- Every coordinate in this document is in **reference units**: a card that is
  364 units wide and 200 units tall. The renderer scales the whole drawing
  uniformly by `frame width / 364`, so a 393 dp card on the phone draws every
  number below at 1.08 dp per unit, and a 320 dp card at 0.88 dp per unit.
- There is no separate compact layout. A smaller allocation is the same card
  drawn smaller. The launcher minimum (250×110 dp, card 200×110 dp) scales to
  0.55 dp per unit and is the same picture.
- Bitmaps are rendered at the launcher's display density with a 1200-pixel
  width cap, then handed to one `ImageView`. Charts, bars and the heatmap are
  drawn straight onto that canvas, never as separate bitmaps that get scaled.
- Content inset: 18 units on the left and right. Content spans x = 18 … 346.
- Corner radius 20 units. Card fill is the palette gradient with the two
  radial glows; edge stroke 1.35 units in the strong line color mixed with the
  palette blue (dark themes) or the strong line alone (light themes).

## Type

Two families, loaded from `res/font`:

| Role | Family | Weight | Size (units) | Case and tracking | Color |
| --- | --- | --- | --- | --- | --- |
| Brand | JetBrains Mono | Bold | 13.5 | Uppercase, +0.04 em | ink |
| Page | JetBrains Mono | Regular | 9 | Uppercase, +0.06 em | muted |
| Section | JetBrains Mono | Bold | 10.5 | Uppercase, +0.06 em | label |
| Status | JetBrains Mono | Bold | 10.5 | Uppercase, +0.04 em | accent when Live, else muted |
| Display | system sans-serif | Bold, tabular | 44 (fits down to 26) | — | ink |
| Figure | system sans-serif | Bold, tabular | 20 | — | ink |
| Stat | system sans-serif | Bold, tabular | 13 | — | ink |
| Body | JetBrains Mono | Regular | 10 | — | ink |
| Body strong | JetBrains Mono | Bold | 10 | — | ink |
| Legend | JetBrains Mono | Bold | 9.5 | — | ink |
| Secondary | JetBrains Mono | Regular | 9 | — | muted |
| Caption | JetBrains Mono | Regular | 7.5 | Uppercase, +0.03 em | muted |
| Axis | JetBrains Mono | Regular | 7 | Uppercase | muted |

These sizes are the concept's measured sizes plus about five percent. JetBrains
Mono advances 0.6 em per character, so a 10-unit body character is 6 units wide;
the column rules below are derived from that.

"label" is the muted color pulled 30 percent toward ink so section titles stay
legible on a launcher without reading as primary text. "muted" is the palette
muted color unchanged. Tabular figures come from the `tnum` feature.

Text never shrinks to fit except the Overview display total. Everything else is
ellipsized at its column width. Token figures in rows use one decimal below
100K and below 100M and none above ("764K", "2.5M", "199M") so number columns
stay narrow.

## Palette roles

All colors come from the dashboard `Palette` for the active theme.

- ink, muted, label: text as above.
- accent: Live status, the Live toggle, the current-day bar, the heatmap's top
  step, the active page dot.
- success, orange, danger: quota bar fill at more than 35, 15–35, and at most
  15 percent remaining.
- Vendor colors (`originalToolColor`): tool and model marks, breakdown bars,
  the Overview segmented bar and legend dots, and the Claude share on the
  seven-day chart. Unknown vendors fall back to blue, orange, purple, yellow in
  row order.
- blue: seven-day bars for days other than today.
- heat ramp: the five palette heat steps, index 0 for an inactive day.
- line and strongLine: rules, dividers, bar tracks, inactive page dots.

## Header (all pages)

| Element | Position (units) |
| --- | --- |
| App icon | 22×22 at (18, 16) |
| Brand "TOKEN MONITOR" | x 48, baseline 30 |
| Page "OVERVIEW · 1/4" | x 48, baseline 43 |
| Refresh icon | 16×16 at (238, 20) |
| Status text | right edge 296, baseline 31 |
| Live toggle | 40×22 at (306, 16), radius 11 |
| Toggle knob | radius 8, center 11 units from the active end |
| Left chevron | center (9, 100), 6 wide, 10 tall |
| Right chevron | center (355, 100) |
| Page dots | y 190, 4 dots, 7 units apart, centered; active radius 2, others 1.5 |

The toggle track is accent at 18 percent over the shell with an accent stroke
when Live; otherwise the overlay color with a strong line stroke and a muted
knob.

Touch targets are separate transparent views in `usage_widget_deck.xml` and are
not drawn: 48 dp edge columns for page changes, 48×48 dp for Refresh 96 dp from
the right edge, and 72×48 dp for Live at the right edge.

## Overview

| Element | Position (units) |
| --- | --- |
| "TOTAL TOKENS" section | x 18, baseline 66 |
| Total (display) | x 18, baseline 104, fits within x 18 … 232 |
| "$1.67 estimated cost" (body, muted) | x 18, baseline 124 |
| Vertical divider | x 243, y 54 … 128, line color |
| Stats column | x 257; three rows at 26-unit pitch starting at row top 50 |
| Stat value (stat) | baseline row top + 12 |
| Stat caption | baseline row top + 22 |
| Segmented tool bar | x 18 … 346, y 135 … 141, radius 3 |
| Legend | three equal slots from x 18; dot radius 3 at slot x + 4, y 154 in the vendor color; text (legend, ink) at slot x + 13, baseline 158 |
| Rule | y 167, x 18 … 346 |
| "THIS WEEK" caption | x 18, baseline 184 |
| Week tokens (stat) | x 88, baseline 184 |
| "· $15.68" (body, muted) | follows the week tokens with one space |

Stats are, in order, messages today, active time today, and the streak; the
seven-day total fills a missing row. The legend names use the tool display
name; the segmented bar uses the same order and colors as the legend.

## Limits

Rows are providers, not windows. The provider with the tightest window comes
first; each row shows that provider's two tightest windows, tightest on the
left. At most two providers.

| Element | Position (units) |
| --- | --- |
| Row 1 top | 52 |
| Row 2 top | 122 |
| Horizontal rule between rows | y 117.5, x 18 … 346 |
| Provider mark | 22×22 at (18, row top + 2), vendor color |
| Left cell text x | 46 |
| Right cell text x | 197 |
| Left cell right edge | 162 |
| Right cell right edge | 346 |
| Title "CODEX · WEEKLY" (bold mono 10, uppercase, +0.04 em, label color) | baseline row top + 14 |
| Percent (figure) plus " left" (regular mono 13, ink) | baseline row top + 36 |
| Bar | y row top + 42 … row top + 46.5, radius 2.25, track strong line |
| Reset (secondary) | baseline row top + 57 |

The title is "Provider · Window" when that fits the cell, otherwise the window
name alone. The percent takes the ink color above 35 percent remaining and the
risk tone below. One provider draws one row. No limits draws the "no limits"
empty state.

## Breakdown

| Element | Position (units) |
| --- | --- |
| Column divider | x 181, y 54 … 186, line color |
| "TOOLS" section | x 18, baseline 60 |
| "MODELS" section | x 192, baseline 60 |
| Tool rows | up to 3, tops at 68, 108, 148 |
| Tool mark | 16×16 at (18, row top − 1) |
| Tool name (body) | x 40, baseline row top + 11, ellipsized 6 units before the tokens |
| Tool share (body strong) | right edge 168, baseline row top + 11 |
| Tool tokens (body) | right edge is the measured left edge of the share minus 8 units, baseline row top + 11 |
| Tool cost (secondary) | same right edge as the tokens, baseline row top + 20 |
| Tool bar | x 18 … 168, y row top + 24 … row top + 28, radius 2 |
| Model rows | up to 4, tops at 68, 98, 128, 158 |
| Model mark | 14×14 at (192, row top) |
| Model name (body) | x 212, baseline row top + 11, ellipsized 8 units before the share |
| Model share (body strong) | right edge 346, baseline row top + 11 |
| Model tokens (secondary) | x 212, baseline row top + 19 |
| Model bar | x 192 … 346, y row top + 23 … row top + 26.5, radius 1.75 |

The tool share column always reserves its measured width plus an 8-unit gap, so
three-digit shares such as 100 percent cannot run into the token figure. Model
names are long, so the models column keeps the whole name on the first
line and moves the token figure under it; the tools column keeps the concept's
single line because tool names are short.

Shares under one percent print as "<1%". Bars use the row's vendor color.
Fewer rows leave the remaining space empty; row pitch never changes.

## Activity

| Element | Position (units) |
| --- | --- |
| Column divider | x 214, y 54 … 186 |
| "7 DAYS" section | x 18, baseline 60 |
| Week tokens (stat) | x 18, baseline 79 |
| "TOKENS · $15.68" (secondary) | follows the week tokens with one space |
| "PEAK 6.1M" (caption) | right edge 204, baseline 79 |
| Chart plot | x 38 … 204, y 92 … 168 |
| Axis labels (axis) | x 18, at the 100, 67, 33 percent gridlines and 0 at the floor |
| Gridlines | dashed, line color, at 33, 67 and 100 percent of the peak |
| Floor | solid strong line at y 168 |
| Bars | 7 columns, bar width 56 percent of the column, radius 2; blue with the Claude share stacked in orange; today in accent |
| Day labels (caption) | centered under each bar, baseline 179 |
| "ACTIVITY" section | x 226, baseline 60 |
| Heatmap | x 226 … 346, y 68 … 132; 13 week columns, 7 rows Sunday first, 2-unit gaps, radius 1.5 |
| Month labels (caption) | baseline 144 at the column where each month starts |
| Rule | y 152, x 226 … 346 |
| Active days (stat) | x 226, baseline 172 |
| "ACTIVE DAYS" caption, or "DAYS" when it does not fit | x 226, baseline 184 |
| Stat divider | x 286, y 158 … 186 |
| Messages today (stat) | x 292, baseline 172 |
| "MESSAGES TODAY" caption, or "MESSAGES" when it does not fit | x 292, baseline 184 |

A day with no observation draws no bar and a dash-length stub in the strong
line color. The axis top is three times the clean step nearest above a third of
the seven-day peak, so the three gridlines carry round labels. Month labels are
drawn only at columns that contain the first day of a month.

## Empty and non-ideal states

- No snapshot: header plus the centered empty message in body muted text,
  wrapped to the content width.
- Missing limits, breakdown or history: the same centered message for that page.
- Saved, Stale, Offline, Connecting, Updating: only the status text and toggle
  change. Figures stay.

## Verification

- `WidgetDeckGridTest` (JVM) checks the scale rule, bitmap cap, and fixed bands.
- `WidgetDeckDesignTest` (instrumented) renders all four pages from the dense
  showcase fixture, which has three tools, four models, four quota windows and
  65 days of history, at 250×110, 320×180 and 360×220 dp in Default and
  Porcelain, and captures the 360×220 renders as the gallery images.
- Before any release, the four gallery images are placed beside the concept
  cards at phone size and approved by the owner. Emulator captures alone do
  not pass the visual gate.
