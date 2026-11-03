# Android v0.85.0 r1

I updated the Android companion for desktop Token Monitor v0.85.0.

## What's new

- **Session tool filter:** narrow Sessions to a tool while keeping conversations
  and their subagents together. The filter combines with search and leaves Home
  and the period total unchanged. Tap the selected tool again for All tools.
- **Device hardware icons:** Devices and Home use the hardware hint reported by
  the Hub. Older or unknown hints use a generic icon; Android does not detect
  another computer's hardware or upload icon preferences.
- **Claude session titles:** restored Desktop titles follow the existing desktop
  sharing consent. Grouped-session search now also respects Android's local
  title preference, so hidden titles cannot produce a search match.

The desktop's session-origin badges, per-device icon overrides, floating-menu
blur and collection fixes remain on desktop. Android does not infer a session's
source device from its name. Senpi and Copilot usage continues through the Hub.

## Updating

Use **Settings → App updates**, or install the signed APK over the current app
without uninstalling. The version code is **850001**, signed with the existing
release key. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the released Node Hub and Worker with synthetic hardware and Claude
title data, including old-device updates, generic resets and consent revocation.
The Android checks cover filter/search interactions, grouped totals, hidden-title
search and hardware icons on emulators. I haven't phone-tested this release or
measured battery use.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
