# Android v0.82.0 r1

I updated Android for desktop Token Monitor v0.82.0, including the corrected
archive readings and cache-estimate behaviour.

## What's new

- **Codex cache readings:** cache-hit usage and speed remain available. The
  estimated expiry countdown is removed, including estimates in older saved
  snapshots, because Codex does not report a reliable expiry time. Claude's
  reported tier estimates still appear until they expire.
- **Archived usage:** the app reads the desktop's corrected totals and observed
  model breakdowns. Totals may decrease when duplicate archive readings are
  reconciled or a lower/zero correction arrives. Missing model evidence stays
  Unclassified; the phone does not guess a proportional split.

The v0.81.0 r2 route, stream and background-refresh protections are retained,
along with the existing languages, settings, widgets and animations. Desktop
quota alerts, plugin updates and macOS requirements stay on desktop.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I haven't phone-tested this release. The earlier r2 phone checks apply to that
build; automated and emulator checks do not establish battery savings.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
