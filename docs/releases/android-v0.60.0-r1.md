# Token Monitor for Android v0.60.0 r1

This release updates the Android companion for desktop Token Monitor v0.60.0.

## What changed

- Sessions now distinguish Running, Finished, and Idle activity. Recent sessions
  also show context-window use when the Hub reports both token values.
- Foreground dashboards request the smaller stream-v2 freshness updates and merge
  them into the last complete snapshot. This keeps live timestamps current without
  replacing detailed usage, device history, or session data.
- Factory credit and allowance limits are accepted alongside existing percentage
  windows. Qwen, NVIDIA, and StepFun model families receive consistent labels,
  colors, and marks.
- The four-page widget keeps one fixed 1.82:1 card and receives a final readability
  pass: consistent supporting text, a clearer Saved state, page dots with more edge
  clearance, better sparse Breakdown spacing, and labeled Activity periods.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: install over the existing signed release to preserve pairing, settings,
  and saved widget data. Do not uninstall first.

The app remains read-only. It does not collect usage on the phone, expose session
transcripts, schedule background sync, request a wake lock, or send data through a
public relay.

See [Install and update](../INSTALL.md) for installation and checksum instructions.
