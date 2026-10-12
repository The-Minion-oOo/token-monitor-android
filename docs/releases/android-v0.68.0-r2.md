# Token Monitor for Android v0.68.0 r2

This revision tightens how the app reaches the Hub, fixes the Pages widget's
controls and smooths several rough edges. The desktop baseline stays v0.68.0.

## What changed

- The Hub secret stays on the network its address belongs to. A Tailscale
  address is tried only while Tailscale is connected, and the home address on
  Wi-Fi, Ethernet or through a VPN such as a Tailscale subnet router, never
  directly over mobile data. A Hub name must resolve to a private address
  before the secret is sent. **Find** searches only the Wi-Fi network.
- A dropped live stream reconnects on the address that was working instead of
  swapping to the other one.
- On the Pages widget, Refresh, Live and the page arrows now line up with what
  is drawn at every widget size and orientation. Status words such as
  OFFLINE and CONNECTING are no longer cut off.
- The Pages widget draws in the background, so page taps no longer block the
  launcher. A widget update the launcher refuses can no longer close the app.
- Live ends straight away if the Hub rejects the secret. Tapping the widget
  after a session expired during sleep starts a fresh hour.
- Today's usage is filed under the desktop's day when the phone and desktop are
  in different time zones, so days are no longer double-counted or overwritten.
- A very large history or device list no longer stops the dashboard from
  connecting, and live updates reuse the decoded history instead of re-reading
  it each time.
- Rotating the phone keeps the selected period. Warning colors are easier to
  read on light themes. Quota percentages round the same way everywhere, and
  large totals roll over cleanly (1.0M instead of 1000.0K).
- Fixed a crash when two limit rows share the same identity, and the Status
  screen now says when service status can't be loaded.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.68.0. Package
`io.github.theminionooo.tokenmonitor`, version code `680002`, signed with the
existing release certificate. Use **Settings → App updates** or install the
APK over your current release. Do not uninstall first.

Build and emulator checks are recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
I haven't installed this revision on my phone yet.
See [installing and updating](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
