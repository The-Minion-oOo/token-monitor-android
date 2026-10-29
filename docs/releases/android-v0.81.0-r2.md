# Android v0.81.0 r2

I tightened Hub connection checks and made failing live streams and skipped
widget jobs do less unnecessary work. Desktop compatibility remains v0.81.0.

## What's changed

- **Private connections:** HTTP Hub reads and discovery bypass configured
  proxies. Tailscale routes require a VPN link with a Tailscale address; an
  unrelated VPN cannot unlock private routes. Private names must resolve to
  addresses in their allowed range, including private IPv6 scopes.
- **Stream recovery:** deeply nested JSON ends the connection safely. Repeated
  disconnects after the opening snapshot retain the reconnect delay. Complete
  frames use their received size without another serialization pass.
- **Periodic widgets:** skipped jobs check eligibility before loading pairing
  and cache data. Deadline failures retain stale data; a slow cache write cannot
  turn an already-applied snapshot into a failed refresh.
- **Currency privacy:** both exchange-rate sources receive the app User-Agent
  rather than Android's device-model-containing default.

Dashboard update frequency, layouts, animations, languages and existing desktop
features are retained.

## Updating

This is a release candidate. When published, install the signed APK over the
existing app without uninstalling, or use **Settings → App updates**. The package
and signing certificate are unchanged; the version code increases to `810002`.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

The hardening code passed phone dashboard, Widget Live and forced periodic
refresh checks over Tailscale and home Wi-Fi in a private signed build. The
final r2 APK has not yet been installed on my phone; that check remains before
publication. Forced jobs do not establish overnight scheduling or battery
savings.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
