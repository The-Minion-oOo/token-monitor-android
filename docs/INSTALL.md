# Install and update

**Current release:** v0.84.0 r1 (`840001`) supports desktop v0.84.0,
including Manus credits and separate Muse web/CLI quotas. The existing
package and release certificate allow an in-place update. I haven't
phone-tested this release; see [release notes](releases/android-v0.84.0-r1.md)
and [Validation](VALIDATION.md) for the checks and their limits.

Open **Settings → App language**, **Display currency**, **Tools and quotas**
or **Live throughput** for the local choices. Language starts with the phone
setting, currency starts in USD and live rates start Off. Automatic rates use
public exchange-rate services while the app is visible; manual rates avoid that
request. **Preview diagnostic report** shows exactly what will be copied/shared.
No report is sent automatically.

## Requirements

- Android 8.0 or newer.
- Token Monitor desktop and a Hub with a shared secret. Desktops connect to a Cloudflare Worker, or one desktop hosts the Hub. Android v0.84.0 r1 is verified against desktop v0.84.0.
- A route to the Hub: Tailscale is recommended; home Wi-Fi and an existing internet HTTPS Hub are optional.

Without Tailscale, follow [Cloudflare Hub: desktop to Android](CLOUDFLARE_HUB.md)
to deploy the free-plan Worker Hub, connect your desktops and pair Android over HTTPS.

## Install

Download the signed v0.84.0 r1 APK from the public
[release](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.84.0-r1),
or use **Settings → App updates** in v0.62.0 r1 or newer. It uses the same package
and release certificate as earlier releases, with a higher version code. An
in-app upgrade on my phone from v0.65.0 to v0.66.0 r1 preserved pairing and the
placed Pages widget; see [Validation](VALIDATION.md). I also updated my phone
from v0.77.0 r1 to r2 through the in-app updater and confirmed that the Pages
widget redrew without opening the app. I didn't recheck pairing, Live updates
or the dashboard update frequency in that test. The older v0.61.0 r1 app has
no in-app updater, so install a newer release manually once. I installed the
signed v0.78.0 r2 APK over r1 on my phone and confirmed pairing, Tailscale and
home Wi-Fi still worked. Earlier widget checks apply only to their named builds.
The r3 confirmation dialog fix was checked on emulators and was not phone-tested.
I haven't phone-tested v0.79.0 r2, v0.80.0 r1 or v0.81.0 r1. For v0.81.0 r2,
I installed the exact release APK over the private hardening build: pairing and
visible settings survived, and the Pages widget redrew before opening the app.
This r2 check used a USB install, not a new in-app updater test.

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

Each signed release includes an APK and a `.sha256` file. Compare the hash:

```powershell
Get-FileHash .\token-monitor-android-v0.84.0-r1.apk -Algorithm SHA256
```

On macOS or Linux, use `shasum -a 256` or `sha256sum` instead:

```sh
shasum -a 256 token-monitor-android-v0.84.0-r1.apk
sha256sum token-monitor-android-v0.84.0-r1.apk
```

A matching checksum detects download corruption; download both files from the
trusted repository release. The release signing certificate SHA-256 is:

```text
eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c
```

Optionally, check the signature as well with `apksigner` from the Android SDK
build tools:

```sh
apksigner verify --print-certs token-monitor-android-v0.84.0-r1.apk
```

The printed SHA-256 certificate digest must equal the value above, which is also
recorded in `release.json`.

## Update without losing pairing

Install the newer release APK over the existing release. The package and signing
identity stay the same, and the internal version code increases. Do not uninstall
first: uninstalling removes pairing, preferences and the saved snapshot.

Starting with v0.62.0 r1, Settings → App updates checks the latest
published GitHub release when opened. For newer releases with an update
manifest, tap **Download and install**. The
app checks the APK size, SHA-256, package, version code, and release signing
certificate before opening Android's installer. Android may ask you to allow
Token Monitor to install apps; return and tap **Install downloaded update** after
granting that permission. Android still asks you to confirm the installation.
If the release lacks an update manifest, use **View Android releases** instead.
For an installation conflict, check that you downloaded a release APK rather than
a debug/preview build. Keep developer previews in the separate `.preview` package.

After updating, open the dashboard once to verify the saved connection. The
original Usage widget remains resizable; the four-page Pages widget keeps one
fixed 1.82:1 composition. Widget Live is an explicit session and may need to be
started again.

The signed r2-to-r4 upgrade retained pairing, home Wi-Fi connectivity and visible
display settings on my phone. Background refresh starts Off with Wi-Fi only on.
