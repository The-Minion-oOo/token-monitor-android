# Token Monitor for Android v0.60.0 r3

This revision makes the four-page widget visibly sharper and more expressive on
high-density launchers while preserving its fixed footprint and real data.

## What changed

- The card now renders at launcher density instead of enlarging a 560-pixel
  bitmap. Text, icons, borders, charts, and the activity grid stay sharp on the
  Galaxy S25 Ultra's One UI launcher.
- Text is larger and more consistent across Overview, Limits, Breakdown, and
  Activity. Only the main total uses a separate display size.
- Brighter cyan, mint, and coral accents improve the border, quota bars, tool
  shares, charts, heatmap, and provider marks.
- Sparse Breakdown rows place usage and percentage values below long names, so
  the information remains legible instead of shrinking or colliding.
- The card remains 1.82:1 on every page, and the larger bitmap stays comfortably
  within Android's documented App Widget bitmap-memory limit.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: install over the existing signed release to preserve pairing, settings,
  and saved widget data. Do not uninstall first.

The app remains read-only. Page changes are local and add no background timer,
network request, service, or wake lock.

See [Install and update](../INSTALL.md) for installation and checksum instructions.
