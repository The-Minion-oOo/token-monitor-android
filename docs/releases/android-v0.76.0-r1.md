# Android v0.76.0 r1

I updated the Android companion for desktop Token Monitor v0.76.0, including
the changes since v0.73.0.

## What's new

- Choose 1–50 items separately for Tools, Models, Sessions and Devices under
  Settings → Main dashboard → Home modules. I kept the existing defaults and saved layout.
  Running or waiting sessions can still appear beyond the chosen count.
- Expand a project to switch between Tools and Models token shares. Usage
  without model attribution is labelled Unknown model, including older Hubs.
- Antigravity subagents stay under their parent conversation. Grok Bot usage
  no longer appears Running just because a recent cloud record arrived.
- Gemini 4 Argon quotas stay separate. Missing quota values say Not reported;
  a real zero still means exhausted. Grok reasoning and classified history
  zeros are checked against the pinned desktop code.
- I included the Compose compiler 2.4.21 patch.

The widget design, digit roll, network rules and on-demand updater are
unchanged. Notch gestures, glass, dock stacking and collection fixes remain on
desktop. Android stays read-only.

## Updating

I signed the APK with the existing release key and increased the version code
to 760001. Use **Settings → App updates**, or install the APK over the existing
app without uninstalling. Android asks you to confirm installation. Android 8
or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the pinned Hub, JVM tests, lint, builds and emulator presentation.
I haven't tested this release on my phone, and battery use is not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
