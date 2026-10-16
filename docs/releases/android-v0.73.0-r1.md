# Android v0.73.0 r1

I updated the Android companion for desktop Token Monitor v0.73.0.

## What's new

- Oh My Pi titles no longer show an unsupported leading font icon. The title
  code and text remain visible, and I haven't changed the stored title.
- I checked Cursor's corrected Included usage percentage, including a true
  zero and unknown weekly usage, against the released desktop probe and Hub.
- Session speed uses the matched timed-output subtotal reported by desktop,
  not all output from a partially timed session. Corrected zero usage replaces
  the earlier complete snapshot without bringing old totals back.
- Claude Desktop usage appears under Claude, and Windows Goose usage under
  Goose, when the collecting desktop reports it.

Widget layouts, digit roll, network rules and Android's on-demand updater are
unchanged. Oh My Pi transcript details, collection fixes, hover previews and
desktop update history stay on desktop. Android remains read-only.

## Updating

I signed this APK with the existing release key and increased the version code
to 730001. Use **Settings → App updates**, or install the release APK over the
existing app without uninstalling. Android asks you to confirm installation.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the pinned Hub, JVM tests, lint, builds and emulator presentation.
I haven't tested this release on my phone, and battery use is not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
