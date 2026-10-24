# Android v0.78.0 r5

I fixed background widget refresh with **Wi-Fi only** on while Tailscale is
connected. Android can treat a VPN as metered even over home Wi-Fi, so the
refresh could stop running without showing an error.

## What changed

- **Wi-Fi only** now decides from the network under Tailscale. Unmetered home
  Wi-Fi is allowed; mobile data, metered Wi-Fi and uncertain routes are still
  skipped.
- The scheduled job asks Android for any network, and each run checks Wi-Fi
  before and during its requests. On mobile data, Android may start the job,
  but it stops before sending anything.
- On Android 8, a validated, unmetered Wi-Fi network must be present.
- An existing schedule switches over on the next update.

Background refresh still starts Off, with Wi-Fi only on. The Hub protocol,
connection routes and widget layouts are unchanged.
[Widget guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/WIDGETS.md#background-refresh).

## Compatibility and updating

Desktop compatibility remains v0.78.0. Install over the existing app to keep
pairing and preferences.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

## Testing

New unit tests cover Wi-Fi, metered Wi-Fi, cellular, Tailscale over Wi-Fi and
over cellular, and Android 8. JVM tests, lint, the debug build and required CI
passed. This release was not phone-tested.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
