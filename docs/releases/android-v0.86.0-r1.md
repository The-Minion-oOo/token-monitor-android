# Android v0.86.0 r1

I updated the Android companion for desktop Token Monitor v0.86.0.

## What's new

- **DeepSeek status:** reads current incidents from the official status feed. The
  old mirror could miss newer outages. Active incidents, maintenance and affected
  components are counted separately; an invalid feed shows Unknown.
- **Billing compatibility:** verified Command Code balances and resets for actual
  28–31-day billing periods through the released Node Hub and Worker.

Quota history is stored locally by the desktop and is not shared through the Hub.
Claude CLI fallback controls, desktop window behaviour and proxy settings stay
on desktop. Sessions filtering, hardware icons, languages and widgets are retained.

## Updating

Use **Settings → App updates**, or install the signed APK over the current app
without uninstalling. The version code is **860001**, signed with the existing
release key. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the released Node Hub and Worker, 457 JVM tests, 132 tests on each
CI API 36/37 emulator, lint and optimized Android 8 launch/resume. I updated my
phone in place, keeping pairing and layout, and checked fresh Tailscale data,
reopening, Sessions filtering and all four service-status cards with the final
signed APK. Battery use, natural background refresh and network handover remain
separate checks.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
