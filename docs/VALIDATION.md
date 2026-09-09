# Validation

The latest signed release is Android **v0.54.0 r20** (`540020`), compatible with
desktop Token Monitor v0.54.0. The repository may contain reviewed changes that
have not yet been packaged as a release.

## Current development checks

The local suite uses the isolated preview package on the API 36
`TokenMonitor_API36_Pixel10ProXL` emulator (`emulator-5554`).

- 64 JVM tests cover protocol parsing, network boundaries, storage and shutdown,
  history aggregation, widgets, and presentation rules.
- 24 instrumentation tests exercise navigation, filtering, period changes, day
  details, search, connection cancellation, widget controls, rendering, and the
  dashboard streaming and widget Live controls.
- Lint completes with no errors. Remaining warnings are reviewed separately and
  are mostly narrow-widget typography or platform-preview guidance.
- Documentation links are checked by `node tools/check-docs.mjs`.

Run the same checks locally:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PtokenMonitorPreview=true
.\gradlew.bat :app:connectedDebugAndroidTest -PtokenMonitorPreview=true
node tools/check-docs.mjs
```

## Physical-device coverage

On September 8, 2026, r19 was installed over an earlier signed release on a
Galaxy S25 Ultra with pairing preserved. The 4×2 and 4×3 widgets and the Live
notification rendered and operated on the phone.

r20 also completed a 25-minute screen-off widget Live measurement on the same
phone. Android attributed about 1.54 mAh to the app and reported 52.7 MB PSS, but
the session received 104.19 MB and posted 698 notification updates. Those results
led to r21's 30-second stats refresh and notification deduplication.

r21 passes the complete emulator suite and a rendered walkthrough of onboarding,
Home, and Hub status on `emulator-5554`. Its minified release build also completes.
A signed upgrade, saved-pairing check, route and offline checks, widget size pass,
and a repeat of the battery/network measurement on the S25 Ultra remain before
publishing the APK.
