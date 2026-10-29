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

Install the signed APK over the existing app without uninstalling, or use
**Settings → App updates**. The package
and signing certificate are unchanged; the version code increases to `810002`.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I installed the exact signed r2 APK over the private hardening build on my phone.
Pairing and visible settings survived, and the Pages widget redrew before opening
the app. The dashboard, Widget Live/Stop and forced periodic refreshes passed
over Tailscale and home Wi-Fi. Saved data remained available offline, and the
dashboard reconnected when Wi-Fi returned. I restored the settings, removed the
test helper and turned debugging off.

This was a functional check of the existing widget size, without a new in-app
installer test, a one-hour expiry wait, overnight scheduling or comparable
battery measurements. The broader language, payment and currency phone follow-up
remains open in [#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94).
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
