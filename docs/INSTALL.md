# Install and update

## Requirements

- Android 8.0 or newer.
- Token Monitor desktop v0.61.0 with Hub hosting enabled and a shared secret.
- A private route to the desktop: Tailscale is recommended; home Wi-Fi is optional.

## Install

The current public build is the signed v0.60.0 r7 APK on the
[Releases page](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.60.0-r7).
v0.61.0 r1 is the current source candidate and will be added there only after
its signed in-place phone upgrade gate passes. Exact completed and pending
evidence is recorded in [Validation](VALIDATION.md).

To build the current source, install JDK 17 and Android SDK 37, then run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`. It is
intended for development and uses a different signing identity from the release
APK.

To install a signed build:

1. Open this repository's [Releases page](https://github.com/The-Minion-oOo/token-monitor-android/releases).
2. Choose a release, read its compatibility notes and download its `.apk` asset.
3. Open the APK. If Android asks, allow installation from the browser or file
   manager you used, then follow the system confirmation.
4. Open Token Monitor and follow [Pairing](PAIRING.md).

There is no Play Store or F-Droid listing.

## Verify the download

Each signed release will include an APK and a `.sha256` file. Compare the hash:

```powershell
Get-FileHash .\token-monitor-android-v0.60.0-r7.apk -Algorithm SHA256
```

A matching checksum detects download corruption; download both files from the
trusted repository release. The release signing certificate SHA-256 is:

```text
eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c
```

## Update without losing pairing

Install the newer release APK over the existing release. The package and signing
identity stay the same, and the internal version code increases. Do not uninstall
first: uninstalling removes pairing, preferences and the saved snapshot.

Settings → App updates opens Releases once a signed build is published. The app
does not silently install updates.
For an installation conflict, check that you downloaded a release APK rather than
a debug/preview build. Keep developer previews in the separate `.preview` package.

After updating, open the dashboard once to verify the saved connection. The
original Usage widget remains resizable; the four-page Pages widget keeps one
fixed 1.82:1 composition. Widget Live is an explicit session and may need to be
started again.
