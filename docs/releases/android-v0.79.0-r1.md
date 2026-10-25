# Android v0.79.0 r1

I added support for desktop Token Monitor v0.79.0, including OmO Native and the
updated OpenCode Console data.

## What's new

- **OmO Native** appears under its own name and cyan tool colour, separate from
  Pi and Oh My Pi. On an existing desktop installation, enable it under
  **Settings → Tools** if it is not already collecting.
- **OpenCode Go quotas and Zen balances** appear in Limits from the desktop
  Console integration. Unreported windows and reset times stay unknown, while
  real zero and negative USD balances are preserved.

The optional HTTPS route, background widget refresh and Tailscale Wi-Fi-only
fix from r5 are retained. Widget layouts and token animations are unchanged.
Notch navigation, display-mode previews and tray changes stay on desktop;
Android remains read-only.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. Pairing and preferences are kept. Android 8 or newer is
required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the released Hub with synthetic data and tested Android's parser and
native presentation. This build was not phone-tested; the earlier phone and
battery observations apply only to the releases named in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
