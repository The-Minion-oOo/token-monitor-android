# Token Monitor for Android v0.68.0 r1

This release follows desktop Token Monitor v0.68.0 and makes incomplete pricing
and Codex Dots coverage visible on the phone.

## What changed

- Costs show the known subtotal plus any unpriced tokens reported by the Hub.
  Totals, tool/model breakdowns, projects, sessions and history use the same rule.
- Widgets mark incomplete costs with `+ ?`, or `— (?)` when no priced subtotal
  is available. Their accessibility descriptions include the unpriced count.
- Codex Dots sessions are labeled **Dots · observed only**. These are local
  observations while the desktop was connected, not a complete usage history.

The fixed-aspect Pages widget, TM2 colors, MiMo product rows, Wi-Fi fallback,
bounded widget Live sessions and read-only Hub connection are unchanged.
Dots collection, alias/pricing edits and Edge Dock improvements stay on desktop.

## Compatibility and updating

Android 8 or newer; verified against desktop v0.68.0, with earlier Hub fixtures
retained. Older Hubs without the new optional fields keep their existing display.
This APK uses the existing package and signing certificate and updates the
installed release in place. Open **Settings → App updates** to download it; Android
asks you to confirm installation. See [Install and update](../INSTALL.md).

Verification includes JVM tests, lint, native emulator checks and the released
Hub contract. Physical Galaxy upgrade and battery behavior were not rechecked
for this release; the detailed evidence is in [Validation](../VALIDATION.md).
