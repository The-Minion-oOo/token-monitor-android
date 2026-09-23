# Token Monitor for Android v0.60.0 r6

This revision rebuilds the common one-tool/one-model Breakdown state after the
installed r5 still looked like a mostly empty dense table on One UI.

## What changed

- A dedicated sparse layout gives token totals and shares large, opposing
  figures with explicit `TOKENS` and `SHARE` captions.
- Tool and model halves use matching baselines, full-width bars, and balanced
  vertical spacing instead of clustering small text at the top of the card.
- Dense multi-row data keeps the compact table layout.
- The card ratio and the other three widget pages are unchanged.
- `docs/WIDGET_SPEC.md` remains the source of truth for the renderer grid.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: version code `600006` installs over r5 and earlier signed builds
  without removing pairing, settings, or widget state. Do not uninstall first.

The app remains read-only. Page changes are local and add no background timer,
network request, service, or wake lock.

## Verification

- 82 JVM tests and 31 API 36 instrumentation tests pass.
- Lint, debug assembly, signed release assembly, and documentation checks pass.
- The signed APK upgrades the existing Galaxy installation in place and keeps
  the existing One UI widget.
- All four widget pages were captured on the Galaxy after the upgrade; the new
  sparse Breakdown composition is visible at the installed 5×2 size.

See [Install and update](../INSTALL.md) for installation and checksum instructions.
