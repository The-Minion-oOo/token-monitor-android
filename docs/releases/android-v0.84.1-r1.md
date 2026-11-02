# Android v0.84.1 r1

I updated the Android companion for desktop Token Monitor v0.84.1.

## What's changed

- **Kiro quota readings:** Android reads the restored credit allowance from
  the desktop Hub, including a reported zero. Update desktop to v0.84.1 for
  its corrected CLI usage probe; sign-in and collection stay on desktop.
- **Copilot reset details:** Chat without a reset date keeps no empty reset
  line on Home or Limits. Finite Premium quotas keep their reported reset.
  I added released-data and native checks for both cases.

Manus credits, separate Muse web quotas and the existing startup safeguards
are included. The Windows installer, dock transitions, notch and provider
login controls are desktop changes.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. The version code is **841001**, signed with the existing
release key. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the actual released Kiro collector with synthetic process outputs,
the Node Hub and Worker, and Kiro/Copilot presentation on emulators. This checks
the v2 probe and its compatibility fallbacks without running a real CLI or
using provider credentials. I haven't phone-tested this release or measured
battery use.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
