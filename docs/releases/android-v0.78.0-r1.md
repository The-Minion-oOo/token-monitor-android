# Android v0.78.0 r1

This is a compatibility update for desktop Token Monitor v0.78.0. Its new
ZCode session titles and subagent links use Android's existing session layout.

## What's new

- ZCode conversations show their saved titles in Sessions and Home when title
  sharing is enabled on desktop. Android's local title setting can hide them.
- Tool- and workflow-created subagents stay under their parent conversation.
  Deliberate forks and side chats remain separate.
- Turning off title sharing on desktop removes the shared titles without
  changing usage totals or conversation groups.

The performance improvements and widget fixes from v0.77.0 r2 are retained.
The widget design, animations and five-second dashboard update limit are
unchanged. Desktop glass, tooltip and collection changes remain on desktop;
Android stays read-only.

## Updating

I signed the APK with the existing release key and increased the version code
to 780001. Use **Settings → App updates**, or install the APK over the existing
app without uninstalling. Android asks you to confirm installation. Android 8
or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked desktop Hub compatibility, unit tests, lint, builds and emulator
behavior. I haven't tested this release on my phone, and battery use is not
measured. [Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
