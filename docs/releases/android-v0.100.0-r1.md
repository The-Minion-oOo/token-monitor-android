# Android v0.100.0 r1

Android now works with desktop Token Monitor v0.100.0. I checked the new
Antigravity context readings against both the built-in Hub and the Cloudflare Worker.

## What's new

- **Antigravity context.** Home and Sessions show context remaining when the
  desktop reports it for a recent Antigravity session. Older Hubs and sessions
  without a reading still show usage without a context label.
- **Weekly usage report.** Open Trends to preview and share a PNG of the last
  seven days, including token usage and estimated cost.
- **Saved Hubs.** Save multiple connections in Settings and switch between them
  without entering their secrets again. Each Hub keeps its own readings and settings.
- **Quota alerts.** Turn on low-quota and reset notifications in Settings.
  They use fresh readings from refreshes already enabled on your phone. There
  is no extra polling, and notifications are off by default.

The redesigned 3D dashboard, Live Flow, account sign-in, and Amp collection
stay on desktop. Android also reads the Codex Work model and Amp usage the Hub
already reports. QR pairing and automatic Home Wi-Fi address recovery are not
part of this release.

## Updating

Use **Settings → App updates**, or install the signed APK over your existing
copy. There is no need to uninstall. See [Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md)
and [Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
