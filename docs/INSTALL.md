# Install and update

## Requirements

- Android 8.0 or newer.
- Token Monitor desktop v0.54.0 with Hub hosting enabled and a shared secret.
- A private route to the desktop: Tailscale is recommended; home Wi-Fi is optional.

## Install

1. Open this repository's [Releases page](https://github.com/The-Minion-oOo/token-monitor-android/releases).
2. Choose a release, read its compatibility notes and download its `.apk` asset.
3. Open the APK. If Android asks, allow installation from the browser or file
   manager you used, then follow the system confirmation.
4. Open Token Monitor and follow [Pairing](PAIRING.md).

There is no Play Store or F-Droid listing for this launch. A private repository
requires a GitHub account with access; making it public is a separate launch step.

## Verify the download

Each release includes an APK and a `.sha256` file. Compare the hash:

```powershell
Get-FileHash .\token-monitor-android-v0.54.0-r21.apk -Algorithm SHA256
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

Settings → App updates opens Releases. The app does not silently install updates.
For an installation conflict, check that you downloaded a release APK rather than
a debug/preview build. Keep developer previews in the separate `.preview` package.

After updating, open the dashboard once to verify the saved connection and resize
any existing widgets if desired. Widget Live is an explicit session and may need
to be started again.
