# Android v0.81.0 r1

I updated Android for desktop Token Monitor v0.81.0, including smaller live
updates, session reasoning effort and personal Volcengine quotas.

## What's new

- **Smaller live updates:** the dashboard now uses the released Hub's v4 stream.
  It applies changes in order, renews session activity clocks and reconnects for
  a complete snapshot if an update is missing or invalid. Older Hubs still work
  through complete snapshots. The five-second dashboard processing limit stays.
- **Session reasoning effort:** Codex and Claude settings such as `high` and
  `xhigh` appear with the model in Home and Sessions. These are reported settings,
  separate from reasoning-token usage, and remain visible after a turn ends.
- **Volcengine personal plans:** Agent Plan and Coding Plan quotas appear as
  separate accounts with the reported session, weekly and monthly windows.
  Login and `arkcli` setup stay on desktop. Missing windows stay absent; a real
  zero remains exhausted.
- **Codex plans:** the desktop's Pro 100, Pro 200 and Pro 500 labels carry through.
  Proma accounting, Dots collection and Grok Build timing improvements arrive
  through the desktop's synchronized data.

The existing languages, payments, display controls, widget layouts and number
animations are retained. Plugins and their catalog remain desktop features.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I haven't phone-tested this release. Smaller frames were verified with synthetic
data against the released Node Hub and Worker; phone bandwidth and battery
savings are not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
