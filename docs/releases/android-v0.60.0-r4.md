# Token Monitor for Android v0.60.0 r4

This revision rebuilds the four-page widget around one measured card grid so the
phone result matches the intended composition instead of merely rendering the old
layout more sharply.

## What changed

- Header, body, swipe-arrow gutters, and pager now have reserved bands shared by
  Overview, Limits, Breakdown, and Activity.
- Limits uses protected row spacing, so reset labels no longer collide with the
  center divider or progress bars.
- Breakdown spaces the Tools and Models columns independently. A sparse column no
  longer forces the denser column to shrink its text.
- The minimum-size card uses concise top-result and summary states instead of
  compressing full medium-card content until labels collide.
- Overview uses a balanced total/stat split and a dedicated bar, legend, and weekly
  summary stack.
- Activity aligns its summary, chart, heatmap, and totals to fixed regions. Subtle
  guides make sparse seven-day history readable without inventing zero values.
- The shared typography remains slightly larger and the existing cyan, mint, coral,
  and provider accents retain their weight at launcher density.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: version code `600004` installs over r3 and earlier signed builds without
  removing pairing, settings, or widget state. Do not uninstall first.

The app remains read-only. Page changes are local and add no background timer,
network request, service, or wake lock.

See [Install and update](../INSTALL.md) for installation and checksum instructions.
