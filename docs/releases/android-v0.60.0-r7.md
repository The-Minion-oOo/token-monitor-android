# Token Monitor for Android v0.60.0 r7

This revision corrects two things a phone inspection of r6 showed with real
multi-tool data.

## What changed

- Dense Breakdown rows use one composition in both columns: name and share on
  the first line, tokens and cost on the second, then the bar, with enough
  room between the lines that nothing touches. Three rows per column line up
  across the divider. The one-tool/one-model sparse layout from r6 is unchanged.
- The app icon in the header, the Limits provider marks and the Breakdown
  vendor marks are centered on the text beside them; they used to sit high.
- Activity shows today's cost in its second stat when the Hub reports no message
  count for the day. A zero there was not a real figure.
- The Pages widget keeps one fixed 1.82:1 composition across Overview, Limits,
  Breakdown, and Activity. It does not use the original Usage widget's responsive
  layouts.

## Compatibility

- Desktop Token Monitor: v0.60.0
- Android: 8.0 or newer
- Upgrade: version code `600007` installs over r6 and earlier signed builds
  without removing pairing, settings, or widget state. Do not uninstall first.

The app remains read-only. Page changes are local and add no background timer,
network request, service, or wake lock.

## Verification

- 83 JVM tests, lint, debug and signed release builds pass.
- The dense showcase was checked at the supported widget sizes and all four
  production gallery renders were regenerated.
- The signed APK was installed over r6 on a Galaxy S25 Ultra without removing
  pairing, preferences, or the placed widget. All four pages were captured on
  One UI at the installed 5×2 size.
- The release keeps the existing signing certificate, so it upgrades r4 and
  later official builds in place.

See [Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md)
for installation and checksum instructions.
