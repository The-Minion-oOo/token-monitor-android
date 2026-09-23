# Token Monitor for Android v0.60.0 r5

This revision rebuilds the four-page widget from a written, measured
specification so the phone shows the approved design rather than another
adjustment of the previous layout.

## What changed

- Overview, Limits, Breakdown, and Activity are drawn from one grid recorded in
  `docs/WIDGET_SPEC.md`. The card scales as a single picture to whatever size
  the launcher grants, so no size has its own composition to drift.
- The widget bundles a small Latin subset of JetBrains Mono. Samsung launchers
  substitute their own monospace face for the system one, which is why earlier
  phone results never matched emulator captures.
- Limits rows are providers, each with its two tightest windows and one mark.
- Breakdown keeps full tool and model names, shows per-tool cost, and keeps the
  same row pitch whether one row or four are present. Its tool values reserve a
  measured gap from the percentage column, including three-digit shares.
- Activity draws the seven-day chart with a round-number axis and the Claude
  share stacked on each bar, and colors the thirteen-week heatmap with the
  dashboard's ramp.
- Eleven vendor logos that Android's path parser rejected are repaired. A Qwen,
  Gemini, Meta, Cohere, Kimi, MiniMax, Doubao, Hunyuan, OpenRouter, xAI or Xiaomi
  mark previously crashed the widget render.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: version code `600005` installs over r4 and earlier signed builds
  without removing pairing, settings, or widget state. Do not uninstall first.

The app remains read-only. Page changes are local and add no background timer,
network request, service, or wake lock.

See [Install and update](../INSTALL.md) for installation and checksum instructions.
