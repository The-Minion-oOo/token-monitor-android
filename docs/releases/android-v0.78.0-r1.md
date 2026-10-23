# Android v0.78.0 r1

I checked this release against desktop Token Monitor v0.78.0.

## What's changed

- ZCode's saved titles and tool/workflow subagents work with the existing
  Sessions and Home rows. Deliberate forks and side chats remain separate.
- I added released-source checks for ZCode title sharing off, on and revoked,
  plus native tests for expanding children and hiding shared titles locally.
- I corrected the internal compatibility label and retained the v0.77.0 r2
  performance improvements, five-second complete-update limit and widget fixes.

Desktop Edge Dock glass, native tooltip changes and Claude CLI probe isolation
remain desktop-only. Android adds no collector, permission or background work.
The widget design and animations are unchanged.

## Updating

Use **Settings → App updates** or install the signed APK over the current copy.
There is no need to uninstall. ZCode titles appear only when title sharing is
enabled on desktop; Android's local title preference can hide them.

I haven't phone-tested this build. The automated checks cover synthetic Hub
data and emulators, not phone battery use or network switching.

[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md)
· [Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md)
