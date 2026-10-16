# Android v0.72.0 r1

I updated the Android companion for desktop Token Monitor v0.72.0.

## What's new

- Cursor's unified plans show Included usage as a weekly or monthly allowance,
  alongside a positive Prepaid balance when one is reported. A balance is money,
  not a percentage. A missing weekly reset stays unknown.
- Grouped sessions show their combined message count, cache-hit percentage and
  output speed. I calculate the percentages and speed from summed counters,
  rather than taking one member's value or averaging percentages.
- A conversation uses its latest member's activity time while keeping its main
  title. A subagent's continuation keeps its role when its parent isn't visible.

Widget layouts, digit roll, network route rules and Android's on-demand update
check are unchanged. Desktop automatic updates, OpenCode transcript handling
and Edge Dock fixes stay on desktop; Android remains read-only.

## Updating

I signed this APK with the same release key and increased the version code to
720001. Use **Settings → App updates**, or install the release APK over the
existing app without uninstalling. Android asks you to confirm installation.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the pinned Hub, JVM tests, lint, builds and emulator presentation.
I haven't tested this release on my phone, and battery use is not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
