# Token Monitor for Android v0.61.0 r1

This release updates the Android companion for desktop Token Monitor v0.61.0.
The Hub read contract remains stable; the compatibility work is concentrated in
versioned fixtures and the shared identity presentation layer.

## What changed

- Verified all five read endpoints and the live stream against the released
  v0.61.0 tag, with a new sanitized full-endpoint fixture.
- Added canonical Xiaomi MiMo and Devin usage labels, Cline and Devin limit
  providers, GitHub Copilot coverage, and the current desktop provider names.
- Added the upstream Cline and Devin marks while retaining a readable label for
  older `micode` data.
- Preserved the four-page widget geometry and typography from v0.60.0 r7.

## Compatibility

- Android: 8.0 or newer
- Desktop Token Monitor: v0.61.0
- Package: `io.github.theminionooo.tokenmonitor`
- Upgrade: version code `610001` installs over v0.60.0 r7 and earlier signed builds

Install the APK over the existing app. Do not uninstall first; uninstalling
removes pairing, preferences, and the saved snapshot. See
[Install and update](../INSTALL.md) for signing and checksum guidance.

## Verification

The exact test, signing, upgrade, and device evidence for the release candidate
is recorded in [Validation](../VALIDATION.md).
