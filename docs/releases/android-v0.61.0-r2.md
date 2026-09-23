# Token Monitor for Android v0.61.0 r2

This Android-only revision adds an on-demand update check for future signed
releases. Desktop Token Monitor v0.61.0 remains the verified Hub baseline.

## What changed

- Settings now checks the latest published Android release when App updates is
  opened, with no background polling.
- For releases carrying update metadata, the app downloads the APK and verifies
  its size, SHA-256, package, version code, and release signing certificate
  before opening Android's installer. Android still requires your confirmation.
- Releases without update metadata remain available through the Releases link.

## Compatibility

- Android: 8.0 or newer
- Desktop Token Monitor: v0.61.0
- Package: `io.github.theminionooo.tokenmonitor`
- Upgrade: version code `610002` installs over v0.61.0 r1 and earlier signed builds

Install the APK over the existing app. Do not uninstall first; uninstalling
removes pairing, preferences, and the saved snapshot. See
[Install and update](../INSTALL.md) for signing and checksum guidance.

## Verification

Completed and pending checks are recorded in [Validation](../VALIDATION.md).
