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

Release checks are in progress. I will update this record with the completed
checks before publication.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
