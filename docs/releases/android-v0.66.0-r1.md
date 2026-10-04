# Token Monitor for Android v0.66.0 r1

The companion now follows desktop Token Monitor v0.66.0 and recognizes
MiniMax Code usage in the existing dashboard and widgets.

## What changed

- MiniMax Code (`mcode`) uses its display label and MiniMax mark. Tool usage
  stays separate from the MiniMax quota provider and model names.
- Current usage and history retain the costs reported by the desktop,
  including its custom-pricing and cache-write corrections.
- Versioned fixtures and regressions cover all Hub read endpoints, full and
  freshness-only stream events, normalized MiniMax windows and untitled sessions.
- Widget layout, session metrics/title visibility, private read-only Hub access
  and bounded Live behavior are unchanged.

Pricing controls, MiniMax API-region selection, transcript handling, device
management and the bundled Tokscale updater changes remain desktop features.
Android does not collect usage, recalculate prices or control the desktop.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.66.0. Package
`io.github.theminionooo.tokenmonitor`, version code `660001`, signed with the
existing release certificate. Use **Settings → App updates** or install the
APK over your current release. Do not uninstall first.

Build, emulator, review and APK-integrity evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
Physical-phone upgrade, pairing/widget retention, Tailscale/Wi-Fi transitions
and battery behavior were not tested for this release.
See [installing and updating](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
