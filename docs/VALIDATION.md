# Validation

The latest published and phone-verified release is Android **v0.54.0 r20** (`540020`). The current
development candidate is **v0.56.0 r4** (`560004`), based on desktop Token
Monitor v0.56.0 while retaining explicit v0.54.0 and v0.55.0 compatibility
coverage. It has been packaged as a locally signed candidate but has not been
published. Its final release gate is installation and launcher inspection on the
Galaxy S25 Ultra; emulator results are not presented as One UI verification.

## Current development checks

The local suite uses the isolated preview package on the API 36
`TokenMonitor_API36_Pixel10ProXL` emulator (`emulator-5554`).

- 72 JVM tests cover v0.54.0 through v0.56.0 protocol parsing, throughput capability
  boundaries, network boundaries, storage and shutdown, history aggregation,
  widget data, and presentation rules.
- 31 instrumentation tests exercise navigation, filtering, period changes, day
  details, search, connection cancellation, both widget families, all four deck
  pages, responsive rendering, theme rendering, and Live controls.
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

The signed v0.55.0 r1 candidate was installed in place on the Galaxy S25 Ultra
on September 10, 2026. Its certificate matched the installed app, and Android
preserved the app UID, original install time, pairing, and existing responsive
widget. The separate pages widget exposed Samsung's `StackView` card-fan
presentation: multiple scaled and offset pages were visible at once, so r1 is
not a publishable candidate.

The v0.55.0 r2 fixed-card experiment was rejected because it replaced the requested
swipe interaction. r5 keeps the native four-item `StackView` and constrains every
adapter child to one wide 4x2 canvas. Instrumentation verifies the same measured
canvas across Overview, Limits, Breakdown, and Activity at small, medium, and
large allocations in both interface themes. The launcher may still scale and
offset rear cards as part of its native stack presentation.

The signed r5 APK reports version code `550005`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`4da0a340cec7e7ef4d188fd30713babe2fbe760d7da1839ba770e5d0b94bc92c`.
It was installed in place on the Galaxy S25 Ultra with UID `10280` and the
September 6 first-install time preserved. The 28-test emulator interaction suite,
including the four-page deck and fixed picker preview, passes. Final launcher
inspection remains pending because the phone locked after installation.

On September 12, the four r5 deck pages were inspected on the Galaxy S25 Ultra.
Samsung allocated a 5-column by 2-row host with a reported 406 by 215 dp size,
while the renderer applied a second 90% height reduction. The v0.56.0 r1 fix
uses the full launcher-provided height within a 1.82:1 wide-card cap and replaces
weighted Breakdown rows with top-aligned content-height rows. Instrumentation
verifies one measured canvas across all four pages and covers the sparse one-tool,
one-model state. Physical-phone inspection of r1 then showed that One UI could
still clip the lower part of each child hierarchy even though the measured roots
matched.

The v0.56.0 r1 APK reports version code `560001`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`70208ae186eb12d1510c196b57f1bc2c5be4d2964f64117dd804315fecdd5415`.

The v0.56.0 r2 candidate replaces each child hierarchy with one complete 1.82:1
bitmap while retaining native StackView swiping and separate 48 dp-or-larger
Refresh, Live, and Open targets. The four frames have identical bitmap dimensions,
and alpha-preserving output is capped at 560 px wide to stay below the per-item RemoteViews
transfer limit. The 72 JVM tests, lint, debug build, and all 29 API 36 emulator
instrumentation tests pass. Physical-phone installation and One UI inspection are
the remaining release gate.

The signed r2 APK reports version code `560002`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`1e2c999c119cf3c03c9ffbf78597be5a5e7aeb223d4d7be5d6f76a18f08d4d4a`.

The v0.56.0 r3 candidate applies one shared typography scale to the four rendered
pages: section headings, primary values, body rows, and secondary labels now match
across page boundaries. Only the overview token total remains larger. Emulator
captures were compared together at the same 360 by 220 dp allocation, and the
full 72-test JVM suite, lint, debug build, and 29-test instrumentation suite pass.

The signed r3 APK reports version code `560003`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`b561066b217bfa5a49856f11e1e328a14ab3a079013cba4c05b1090192175d7e`.
Physical-phone installation and One UI inspection remain pending because the
phone was not visible over ADB during final packaging.

The v0.56.0 r4 candidate replaces the launcher-controlled collection with one
full-size 1.82:1 card. Forty-eight-dp left and right edge targets change the
locally stored page, and four dots show the current position. A hosted-widget
test verifies both buttons change the rendered page; page changes do not start
a request, timer, service, or wake lock. The old collection service and mutable
PendingIntent template are removed. The 72 JVM tests, lint, debug and signed
release builds, documentation checks, and all 31 API 36 instrumentation tests
pass. Design QA compared the selected mockup with the final Android render and
passed after navigation gutters were added.

The signed r4 APK reports version code `560004`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`5aee9e2bd59582f08abab295c76272b8cbc994b346e55b1f79a7d7ec009216e4`.
Physical-phone installation and One UI inspection remain pending because only
the API 36 emulator was visible over ADB during final packaging.

The phone's usage history also records a Token Monitor widget Live foreground
service from about 01:54 to 05:15 on September 10, substantially longer than the
one-hour requested session. Current battery statistics were reset after that
interval and cannot assign its exact mAh share; the production service was not
running when the v0.55.0 r3 candidate was installed.

The September 12 battery investigation instead identified
`com.android.cli.interact.instrumentation` as the material drain source. Retained
charged-cycle statistics attribute about 32h 11m of foreground-service time and
about 10h of CPU time to that package. Samsung's battery UI independently showed
13h 37m of background time and 3h 27m of CPU for the current day, with no screen
time or wakes. The helper is test tooling rather than part of Token Monitor; it
must be force-stopped and uninstalled after the final physical-phone capture.
It was absent during the final r4 emulator cleanup, and the emulator was shut down.
