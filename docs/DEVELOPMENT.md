# Development guide

Read [Architecture](ARCHITECTURE.md) before changing runtime behavior and
[Hub protocol compatibility](PROTOCOL.md) before changing network models.

## Toolchain

| Requirement | Version |
| --- | --- |
| JDK | 17 or newer |
| Android SDK | Platform 37 (`37.0` in the SDK package feed) and current build tools |
| Gradle | Wrapper included in the repository |
| Node.js | 22 or newer for fixture, contract, and release tools |
| Emulator | API 36 Google APIs image recommended |

`local.properties` must point to the Android SDK and is ignored by Git.

## Common commands

Run these from the repository root. Use `./gradlew` instead of
`.\gradlew.bat` on macOS or Linux.

```powershell
# Unit tests, lint, and debug APK
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Emulator interaction suite
.\gradlew.bat :app:connectedDebugAndroidTest -PtokenMonitorPreview=true

# One JVM test class
.\gradlew.bat :app:testDebugUnitTest --tests "*HubDiscoveryTest*"

# Release metadata and tooling regressions
node tools/check-release.mjs
node --test tools/tests/*.test.mjs
```

Debug output is written to `app/build/outputs/apk/debug/`. Lint reports are in
`app/build/reports/`.

## Preview package

Debug and release APKs use different signing keys. Build the isolated preview
package when a release is already installed:

```powershell
.\gradlew.bat :app:assembleDebug -PtokenMonitorPreview=true
```

It installs as `io.github.theminionooo.tokenmonitor.preview` and has separate
pairing and preferences. Never use the preview flag for a release.

## Fixture Hub

The local fixture serves the sanitized version selected by `upstream.json` on
port 17321 with the secret `fixture-secret`:

```powershell
node tools/fixture-hub.mjs
```

An Android emulator reaches the host at `http://10.0.2.2:17321`. Enable the
private Wi-Fi option in the app for this address.

### Showcase captures

README images use a richer made-up dataset:

```powershell
$env:TOKEN_MONITOR_SHOWCASE = "1"
$env:TOKEN_MONITOR_FIXTURE_PORT = "17322"
node tools/fixture-hub.mjs
```

Pair the preview app with `http://10.0.2.2:17322` and secret `showcase`. Capture
the rendered app or widget; do not assemble screens from design mockups. The
source dataset is `tools/showcase-data.mjs` and the image compositor is
`tools/build-showcase.mjs`. Unset both environment variables afterwards.

For the Pages widget, capture Overview, Limits, Breakdown, and Activity at the
same launcher allocation. Keep all four production renders together in README
marketing so typography, card geometry, navigation controls, and empty-space
handling can be compared directly.

Never put a real Hub secret, address, account, device, project, or usage response
in a fixture or screenshot.

## Tests

JVM tests cover protocol parsing, private-address rules, endpoint failover,
stream recovery, ordered cache writes, history aggregation, presentation rules,
and preferences. Versioned Hub fixtures live under
`app/src/test/resources/protocol/`. The v0.68.0 set covers all five read
endpoints, a complete stream frame, and a freshness-only frame. The v0.67.0
fixtures retain MiMo Console and Membership identities, native-currency
balances/spend and additive shared-settings revisions. Earlier fixtures retain MiniMax Code,
title-free sessions, pricing, counters, cache components and prompt-cache estimates.
Earlier sets retain fx, Muse, Grok, Cursor Auto and ZCode/OpenCode regressions.

The v0.68.0 fixtures add explicit unpriced counts on current and historical
usage, tool/model attribution and observed-only Dots sessions. Regression tests
cover freshness retention, live-day replacement, rolling ranges, tool filters
and legacy zero-cost display. The released-source harness checks these fields
against the actual Hub, including exclusion of private local-session keys.

Instrumentation tests use the preview package to exercise navigation, dialogs,
widgets, storage, and lifecycle behavior. Run them on an API 36 emulator before
handing off a meaningful UI or lifecycle change. Visual changes also need a
native screenshot at the affected phone or launcher size.

Current coverage and physical-device limits are recorded in
[Validation](VALIDATION.md).

### Released Hub contract

The fixture server is useful for repeatable UI checks, but cannot detect a
mistake shared by the Android parser and its hand-written fixtures.
`tools/check-hub-contract.mjs` exercises the actual desktop Hub at the released
commit pinned in `upstream.json`. Review that source before running it:

```powershell
$desktopSource = 'C:\path\to\token-monitor'
$upstream = Get-Content upstream.json -Raw | ConvertFrom-Json
node tools/check-hub-contract.mjs --upstream-repo $desktopSource --approved-commit $upstream.commit
$env:TOKEN_MONITOR_CONTRACT_DIR = (Resolve-Path app/build/hub-contract).Path
try {
    .\gradlew.bat :app:testDebugUnitTest --tests '*ReleasedHubContractTest*'
} finally {
    Remove-Item Env:TOKEN_MONITOR_CONTRACT_DIR
}
```

The harness exports committed Hub/shared files into a temporary directory,
starts a loopback Hub with synthetic records, and checks authentication, all
five read endpoints, snapshot delivery, freshness, and changed statistics. It
does not use the desktop working tree, collectors, saved pairing, or real data.
Responses are written to `app/build/hub-contract/`; the temporary Hub is stopped
and its source/data directory removed afterwards.

`ReleasedHubContractTest` feeds those responses through the Android parser and
stream reducer. Gradle tracks the supplied directory as a test input. Without
`TOKEN_MONITOR_CONTRACT_DIR`, that one test is skipped; the versioned fixture
tests still run normally. The contract workflow supplies it explicitly.

### Synthetic performance baseline

`PerformanceBaselineTest` generates statistics around 512 KiB, 1 MiB, and just
under 2 MiB, with 365 history days and 12 months. It records cache hydration,
complete parsing, freshness processing, and each Pages widget's bitmap drawing
time. Run it on the isolated preview package:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PtokenMonitorPreview=true
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class io.github.theminionooo.tokenmonitor.PerformanceBaselineTest io.github.theminionooo.tokenmonitor.preview.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as io.github.theminionooo.tokenmonitor.preview cat files/performance/performance-baseline.json
```

The first hydration follows a synthetic cache write. It is not a process or
filesystem cold start. Widget timings cover an off-screen software bitmap,
excluding launcher updates and GPU composition. These are diagnostic timings
with no pass/fail threshold; compare the same device and build type. They do
not establish release startup speed, network consumption, or battery life.

## Engineering rules

- Keep Hub access read-only. The separate updater reads only published GitHub
  release metadata and downloads the signed APK when the user asks to install.
- Validate every saved address before sending the bearer secret. Reject redirects.
- Keep secrets out of UI state, logs, fixtures, screenshots, and issue reports.
- Share one repository between the visible dashboard and an explicit widget
  session. Stop it when neither owner needs data.
- Queue cache writes and finish the last save or clear before repository shutdown.
- Treat unknown Hub fields as compatible; add a new fixture directory when the
  verified desktop protocol changes.
- Use `LocalPalette`, shared formatting helpers, and `LocalNow` rather than
  creating parallel presentation rules in a screen.
- Keep motion interaction-driven and honor reduced-motion settings.
- Add dependencies only when the platform or existing libraries cannot provide
  the behavior cleanly.

## Adding features

### Hub fields

Add the field to the tolerant DTO, map it once in `HubProtocolParser`, expose it
through the domain model, add it to the appropriate versioned fixture, and test
the mapping before using it in UI.

### Stream events

Keep delivery semantics in `HubStreamProtocol`. A complete event may pass through
the stable snapshot parser; an incremental event must explicitly whitelist the
fields it can replace and preserve the rest of the last complete snapshot. Add a
fixture and reducer test before wiring the event into `HubRepository`.

### Presentation semantics

Put reusable rules such as session activity windows, context percentages, and
vendor families in small presentation helpers. Test those helpers independently;
screens should render the result rather than re-interpret protocol fields.

### Dashboard views

Add the destination, icon, `DashboardContent` route, and default display option.
Keep screen-specific state in the screen or ViewModel and shared formatting in
the existing presentation helpers.

### Tools and model vendors

Extend `vendorOf`, `originalToolColor`, and `upstreamToolAsset` where an upstream
mark exists. Marks are monochrome vector resources named `upstream_logo_*`.

### Display settings

Add a default to `DisplayOptions`, persistence in `DisplayPreferences`, a
ViewModel pass-through, and the control and collapsed summary in Settings.

## Continuous integration

- **Android checks:** documentation links, release metadata, Node tooling tests,
  JVM tests, lint, and debug assembly on pull requests and pushes to `main`.
- **Android interaction checks:** the full preview suite on API 36 and API 37
  (SDK package `37.0`), with a JUnit report check requiring all 46 distinct tests
  and no failures or skips; and optimized APK install/launch/resume on API 26. Update
  the expected count when adding or removing instrumentation tests. The API 26
  build uses a disposable CI signing key and is not a distributable upgrade.
- **Released Hub contract:** the pinned desktop source and Android parser test
  above, on `main` pushes or explicit dispatch on `main`. It does not execute
  PR-selected upstream code. The workflow's literal source pin must agree with
  `upstream.json`.
- **Upstream release check:** weekly comparison with the desktop release in
  `upstream.json`; opens a review issue for a newer stable release and closes
  older automated reminders already covered by the verified baseline. Manual
  issues and previously closed review decisions are retained.
- **Dependabot:** weekly Gradle and GitHub Actions updates.

Workflow configuration describes intended coverage. Check the actual run and
its commit before recording a pass in [Validation](VALIDATION.md).

Release signing and publishing are documented in [Releasing](RELEASING.md).

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Use the separate preview package; debug and release keys differ. |
| Emulator cannot reach the fixture | Use `10.0.2.2`, not `127.0.0.1`. |
| Gradle cannot find the SDK | Create `local.properties` with `sdk.dir=<Android SDK path>`. |
| Find on Wi-Fi is slow in the emulator | The simulated network can take several seconds. |
| A desktop address starts with `169.254` | Use the `100.x` address shown by Tailscale instead. |

Android permits only one active UiAutomation connection. Stop interactive CLI
inspection before running instrumentation tests.
