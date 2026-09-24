# Token Monitor for Android v0.62.0 r1

This release brings the Android companion to desktop Token Monitor v0.62.0 and
adds an on-demand update check for future signed Android releases.

## What changed

- Verified the v0.62.0 Hub read contract with sanitized examples for all five
  read endpoints and the stream. Pi and Oh My Pi remain separate in usage views.
- Added TypeSafe's provider label, plan, and reported balance to Limits; Devin's
  reported plan also remains visible. Unavailable optional details are ignored.
- App updates checks the latest published Android release when opened, without
  background polling. For later releases with update metadata, it verifies the
  downloaded APK's size, SHA-256, package, version code, and release signing
  certificate before opening Android's installer. Installation still requires
  user approval.

## Compatibility

- Android: 8.0 or newer
- Desktop Token Monitor: v0.62.0
- Package: `io.github.theminionooo.tokenmonitor`
- Upgrade: version code `620001` installs over v0.61.0 r1 and earlier signed builds

Install the APK over the existing app. Do not uninstall first; uninstalling
removes pairing, preferences, and the saved snapshot. See the
[install guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md)
for signing and checksum guidance.

## Verification

Completed and pending checks are recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
