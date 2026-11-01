# Android v0.84.0 r1

I updated the Android companion for desktop Token Monitor v0.84.0.

## What's new

- **Manus credits:** view the daily/monthly allowance and spendable credit
  balance reported by the desktop, including a reported zero. Missing readings
  stay unavailable. Signing in and collecting credits happen on desktop.
- **Muse web quotas:** Muse and Muse Code have separate labels and quota rows.
  Muse Code keeps its device observations; the web product keeps its row state
  when the reporting device changes. Additional tokens use the website's
  reported text, with the dollar balance available as a fallback.
- **Desktop rate correction:** Android reads the corrected output-rate counters
  through the existing Hub protocol. Empty completions do not become an
  invented rate or additional measured tokens on the phone.

The v0.83.0 r2 startup safeguards are included. Dock dragging, provider sessions
and plugin rate/coverage resources remain desktop features.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. The version code is **840001**, signed with the existing
release key. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the released Node Hub and Worker with synthetic provider responses,
including zero, missing, expired and cross-device cases, and checked Home and
Limits on emulators. I haven't phone-tested this release or measured its battery
use. The earlier r2 phone check applies to that build, not this update.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
