# Validation

This page records how I check each Android release and what those checks do and
do not cover. I report emulator and CI results as exactly that, and I never
describe them as results from a physical phone. Phone evidence applies only to
the build it names.

## Current release: v0.81.0 r1

I published source `a8929c741d30613b0018a177641fbdc958b616c9` from
[PR #104](https://github.com/The-Minion-oOo/token-monitor-android/pull/104),
targeting desktop v0.81.0 at `ce89af774664f3852aa605350b59732247699650`.
All required checks passed on that exact main commit:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40921750990),
[API 36/37 and optimized API 26](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40921750616),
and [released Node/Worker contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40921750906).
The reports confirm **353 JVM tests** and **100 interaction tests on each of
API 36 and API 37**, without failures or skips. Local lint, debug/test and signed
R8 builds, 14 tooling tests, the full API 36 suite and optimized API 26 smoke
passed. I inspected the new native rows and reviewed the full diff. Ordered
v4 updates, freshness clocks, nulls, removals, recovery and personal quotas were
checked against both released Hub implementations using synthetic inputs.

The published APK is `token-monitor-android-v0.81.0-r1.apk`, **3,362,973 bytes**,
package `io.github.theminionooo.tokenmonitor`, version code **810001**.
SHA-256: `cc8bf0e5bd8c303f12f76d37a16c45bcc57d15f37d5936b874d9d4d87fb43a08`. The release certificate remains
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
Draft and anonymous public downloads matched the staged APK, checksum and update
manifest. [Android v0.81.0 r1](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.81.0-r1) is Latest.

I haven't phone-tested this release. Phone upgrade retention, route transitions
and comparable bandwidth/battery measurements remain unverified;
[#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94) remains
a nonblocking follow-up. Synthetic frame sizes do not establish phone savings.

## How each release is checked

Every release goes through the same automated gate before I publish it:

- **Unit tests and builds:** 370 JVM tests, run with the generated Hub contract
  supplied and nothing skipped, plus Android lint, the preview debug build and
  the instrumentation APK.
- **Instrumentation suite:** 103 tests on API 36 and API 37 emulators. A report
  check requires every test to be present with no failures, errors or skips.
- **API 26 smoke check:** the optimized R8 release APK installs, launches and
  resumes on the oldest supported version. This is not full Android 8
  interaction coverage.
- **Released-Hub contract check:** I run the pinned upstream Hub release with
  isolated synthetic data, read all five endpoints with authentication, and
  capture complete, freshness-only and changed-stat stream events. Android's
  parser consumes those real responses alongside fixtures from earlier
  upstream versions. The check also confirms that session titles are stripped
  by default and that synthetic prompt and message sentinels never appear in
  responses.
- **Tooling and docs:** Node tooling tests, the documentation link check
  (`node tools/check-docs.mjs`), release metadata checks and a Git whitespace
  check.
- **Release bundle:** package name, version code, signing certificate, and
  agreement between the APK, checksum and update manifest. I verify these on
  the staged draft and again after downloading from the published release.

`release.json` lists the required CI jobs: `build`, `interaction-api36`,
`interaction-api37`, `optimized-api26` and `contract`. All of them must pass on
the exact `main` commit that gets tagged. None of these checks uses a private
Hub or a vendor account. [Releasing](RELEASING.md) covers the process and
[Development](DEVELOPMENT.md) covers running the checks locally.

## Earlier release: v0.78.0 r4

I released optional background widget refresh, starting Off with Wi-Fi only on.
Required build, API 36/API 37 interaction, optimized API 26 and released-Hub
contract CI passed on source `d32e91668d50abe740e4f9abb943ae1082446d36`. Local checks passed 301 JVM tests,
lint, debug and signed release builds, 14 tooling tests and release bundle validation.
I reviewed the full diff. The signed production upgrade from r2 retained pairing,
home Wi-Fi connectivity and visible display settings; refresh stayed Off.

The preview phone completed eight natural overnight runs with 2,088,840 response
bytes and 8.229 seconds of total job time. Its persisted hourly Wi-Fi-only job
returned after reboot without opening the app; a bounded read succeeded.
Android's approximately 1.12 mAh attribution is an estimate, not a direct measure
of the feature's incremental battery cost. I did not measure a matching Off
baseline.
Temporary preview components were removed after testing.

APK SHA-256: `3a7400377c3512020e2bb9f52d8eeacb7dc36566805132ac81d44c30f15e4bf7`.
Package and release signing certificate remain unchanged. I checked the staged
and published APK, checksum and update manifest together.
[Android v0.78.0 r4](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.78.0-r4)
was the Latest release until r5.

## Earlier release: v0.78.0 r3

I added a default-Cancel confirmation before disabling a saved internet Hub.
Cancel, Back and outside taps leave pairing unchanged; private main routes and
initial pairing keep the plain toggle. I checked the light and dark dialogs,
including opening confirmation from an address field with the keyboard active.
This small UI revision was not phone-tested. Earlier phone evidence remains
attached to r2 and does not establish r3 phone behavior.

All required checks passed on released source `21776feb5279a77bdddda7286d5aca33435376aa`:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40051712692),
[API 36/37 interaction and optimized API 26](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40051712682),
and [Hub contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40051712673).
The gate covers 294 JVM tests, 78 interaction tests on each API 36/37 emulator,
lint, debug assembly, optimized API 26 installation/launch/resume, and 14 tooling
tests. I also built and checked the signed release APK and reviewed the full diff.

The published APK is `token-monitor-android-v0.78.0-r3.apk`, 2,519,099 bytes,
package `io.github.theminionooo.tokenmonitor`, version code `780003`.
Its SHA-256 is `f9ed6494b7cebdecff9a252a8f064ec4cf2d52c5d3f43bb6875fdb9c3c4f8323`;
the release certificate remains
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
Draft and anonymous public downloads matched the staged APK, checksum and update
manifest. [Android v0.78.0 r3](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.78.0-r3)
was the Latest release until r4.

## Earlier release: v0.78.0 r2

The optional internet HTTPS route starts off. I checked the pinned desktop
v0.78.0 Node and Worker Hubs with synthetic data. All required checks passed on
released source `cc2e4fe566c626bace6d62ed3eaa284a083c1ba1`:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40046268701),
[API 36/37 interaction and optimized API 26](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40046268671),
and [Hub contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40046268654).
Local checks passed 294 JVM tests without skips, lint, debug/test and signed
release assembly, 73 API 37 interaction tests and 14 tooling tests.

On my phone, the preview UI/storage checks and synthetic HTTPS snapshot,
freshness and changed-stats tests passed over cellular with Wi-Fi and VPN off.
The dashboard updated without a manual refresh. I closed the disposable endpoint.
The signed r1 → r2 production upgrade retained the main address, home fallback
and secret; Tailscale and Home Wi-Fi both connected. Internet consent stayed off.
I restored the original Wi-Fi/VPN state. Widget expiry, battery use and long-term
connection reliability were not remeasured for this revision.

The published APK is `token-monitor-android-v0.78.0-r2.apk`, 2,519,092 bytes,
package `io.github.theminionooo.tokenmonitor`, version code `780002`.
Its SHA-256 is `b5e47918c142c7fbc46b6a7ae86ec1d96f92ae0b20e353c8ee365b2bc422f961`;
the release certificate remains
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
Draft and anonymous public downloads matched the staged APK, checksum and update
manifest. [Android v0.78.0 r2](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.78.0-r2)
was published before r3.

## Earlier release: v0.78.0 r1

I checked desktop tag `v0.78.0`, commit
`d52e0b9cf970a03a30a5f5a1e9139693bb211cd8`, including the released ZCode
metadata resolver and Hub. Tool/workflow children group under their parent;
forks and side chats stay separate. Titles follow desktop consent and Android's
local display preference. The v0.77.0 r2 performance fixes, five-second complete
update limit, widgets, animations, permissions and network behavior are retained.

Local checks passed: 284 JVM tests with generated responses and zero skips,
lint, debug/test and production-signed builds, all 70 tests on API 36 and API 37,
14 tooling tests, documentation, release metadata and whitespace. I inspected
the native expanded ZCode capture and reviewed the full diff. The final signed
APK installed, launched and resumed on API 26. I haven't phone-tested this build
or measured its battery use.

The release tag points to `main` commit
`90e6546125f4844125a909e7689b7c70f0e7bb9e`, merged through
[PR #67](https://github.com/The-Minion-oOo/token-monitor-android/pull/67).
All required checks passed on that exact push:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40023322764),
[API 36/37 interaction and optimized API 26 smoke checks](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40023322768),
and [released Hub contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/40023322766).
Downloaded reports confirm 284 JVM tests and 70 instrumentation tests on each
API 36/37 emulator, with no failures, errors or skips.

- APK: `token-monitor-android-v0.78.0-r1.apk`, 2,518,621 bytes
- Package: `io.github.theminionooo.tokenmonitor`, version code `780001`
- File SHA-256:
  `923f5d6babcda54443bd23e0fdf9e4edf35645f65e5a246037cca9025fcd7b58`
- Certificate SHA-256:
  `eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`

The draft and anonymous public APK, checksum and updater manifest match the
staged bundle byte-for-byte.
[android-v0.78.0-r1](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.78.0-r1)
was published before r2. Earlier phone evidence remains attached to the releases
named below, not to this build.

## Release history

Each row is a published release. The phone column says whether I installed that
build on my phone. Builds before v0.60.0 r7 are recorded in the
[changelog](../CHANGELOG.md). v0.60.0 r5 and r6 were unpublished candidates.

| Release | Date | Desktop baseline | Main checks | Phone check |
| --- | --- | --- | --- | --- |
| v0.81.0 r1 | 2026-10-29 | v0.81.0 | Ordered v4 streams, effort and personal quotas; 353 JVM / 100 tests per API 36/37; optimized API 26 | Not phone-tested; phone bandwidth/battery unmeasured |
| v0.80.0 r1 | 2026-10-26 | v0.80.0 | Muse device quotas, Ollama credits/spend, Claude accounts; 339 JVM / 98 tests per API 36/37; optimized API 26 | Not phone-tested; #94 remains open |
| v0.79.0 r2 | 2026-10-26 | v0.79.0 | Payments, provider details, local display controls, currency, diagnostics, live rates and 12 languages; 330 JVM / 95 tests per API 36/37; optimized API 26 | Not phone-tested; nonblocking #94 |
| v0.79.0 r1 | 2026-10-25 | v0.79.0 | OmO Native, OpenCode Console quotas/balances, Node/Worker contracts | No: 86-test API 36/37 suites and optimized API 26 smoke |
| v0.78.0 r5 | 2026-10-24 | v0.78.0 | Wi-Fi-only refresh follows the network under Tailscale | No: not phone-tested; unit tests cover the network cases |
| v0.78.0 r4 | 2026-10-24 | v0.78.0 | Optional background widget refresh, bounded persisted job, Wi-Fi/data controls | Yes: signed r2 upgrade; preview overnight refresh and reboot restoration |
| v0.78.0 r3 | 2026-10-23 | v0.78.0 | Confirmation before disabling a saved internet Hub | No: small UI change; emulator coverage in light/dark themes |
| v0.78.0 r2 | 2026-10-23 | v0.78.0 | Opt-in internet HTTPS, system TLS trust, Node/Worker contracts | Yes: signed r1 upgrade retained pairing; Tailscale/home fallback and synthetic cellular HTTPS/SSE |
| v0.78.0 r1 | 2026-10-23 | v0.78.0 | ZCode grouping, title consent/revocation, released Hub endpoints and stream | No |
| v0.77.0 r2 | 2026-10-21 | v0.77.0 | One complete stream update every five seconds | Yes: in-app update from r1; Pages widget redrew without opening the app |
| v0.77.0 r1 | 2026-10-21 | v0.77.0 | Dashboard JSON/UI preparation, Live ownership, shared widgets and chart sizing, New API spend | No |
| v0.76.0 r1 | 2026-10-20 | v0.76.0 | Home list counts, project model shares, Antigravity quotas and Grok Bot activity | No |
| v0.73.0 r1 | 2026-10-16 | v0.73.0 | Oh My Pi titles, Cursor current-period quotas and matched timing | No |
| v0.72.0 r1 | 2026-10-16 | v0.72.0 | Cursor unified allowances and grouped-session metrics | No |
| v0.71.0 r1 | 2026-10-14 | v0.71.0 | Goose, Notion allowances and separate WorkBuddy accounts | No |
| v0.70.0 r1 | 2026-10-14 | v0.70.0 | Digit roll, CatPaw, MiniMax pay-as-you-go balances | No |
| v0.69.0 r1 | 2026-10-12 | v0.69.0 | Session grouping, Waiting for you, live-activity clock | No |
| v0.68.0 r2 | 2026-10-11 | v0.68.0 | Route rules, Pages widget touch targets and status text, Live and parsing fixes | No |
| v0.68.0 r1 | 2026-10-08 | v0.68.0 | Unpriced-usage subtotals, Dots provenance | No |
| v0.67.0 r1 | 2026-10-06 | v0.67.0 | Separate MiMo products, CNY wallet values, custom TM2 themes | No |
| v0.66.0 r2 | 2026-10-04 | v0.66.0 | Local-network permission flows, chart controls, live-update performance | No |
| v0.66.0 r1 | 2026-10-04 | v0.66.0 | MiniMax Code usage, untitled-session fallback | Yes: in-app update from v0.65.0 kept pairing and widget |
| v0.65.0 r1 | 2026-10-02 | v0.65.0 | fx usage, session counters, cache estimates | No |
| v0.64.0 r1 | 2026-09-30 | v0.64.0 | Muse, Grok and StepFun fixtures, Home recent sessions | No |
| v0.63.1 r1 | 2026-09-28 | v0.63.1 | Model normalization, reasoning-token totals | No |
| v0.63.0 r1 | 2026-09-27 | v0.63.0 | Cursor titles, Antigravity source | No |
| v0.62.0 r1 | 2026-09-24 | v0.62.0 | pi and omp, TypeSafe and Devin fixtures, in-app updater | Yes: in-place upgrade kept pairing and widget |
| v0.61.0 r1 | 2026-09-23 | v0.61.0 | Provider and client compatibility, current desktop labels | Yes: in-place upgrade kept pairing, preferences and widget |
| v0.60.0 r7 | 2026-09-22 | v0.60.0 | Pages widget Breakdown and Activity layout | Yes: installed over r6, widget kept |

APK SHA-256 for earlier published releases (the current release is listed above). All of
them use package `io.github.theminionooo.tokenmonitor` and the same signing
certificate, SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.

- v0.80.0 r1 (`800001`): `d1005df329c257b66448518d58568ed881e77a5ebd8edd2423fe3139a75c5c6b`
- v0.79.0 r2 (`790002`): `e67e63167f622f49c38ace5c821df655279431671b64b2a078e417d2540170be`
- v0.78.0 r5 (`780005`): `6acb35da901df68991e80c4db40118672a22d82613b11c435658bfb32e18402c`
- v0.77.0 r2 (`770002`): `9652f7e76817e26a5c78348af91de480c607892f8e7755f664be50d841ecbc88`
- v0.77.0 r1 (`770001`): `cfb3ba4e2d0cd6eb1ead8fb74f1b5ae2f18f87b356d4b097597cb1fcb3facb4c`
- v0.76.0 r1 (`760001`): `15133c737ddfa758d56b958dc75f556e11e69226c90ff43a5e05106af59e9372`
- v0.73.0 r1 (`730001`): `a80f4ca0c13655cb99d432cac2f8eb80d88b718efb007aa4a81d3275ae94537a`
- v0.72.0 r1 (`720001`): `8ef9a266882fe82082c3d0b58cc99980dbad8026cfa4d6237d19c5a8fd2a32b7`
- v0.71.0 r1 (`710001`): `f6e71f7e7ae45f7d50abdb3c26183efc963230d1f9ee3d7ca52473757d2cff89`
- v0.70.0 r1 (`700001`): `bfc4882613169f6090eb381c512acb86d3f0ba1228724df52e4c4e4491648ffd`
- v0.69.0 r1 (`690001`): `b188411aa93abc478802dd7f5e33d7a0e6653cdbf7ada2dba38057dee174f3cd`
- v0.68.0 r2 (`680002`): `01fbedc6bdb0dda0dea11a0bc46ff4eb290969ed3adbc40005e08f13e53aab43`
- v0.68.0 r1 (`680001`): `81b97ff5ce2947c2d18539b0b166808405a7d812c58b743b3c98f525e1ec8663`
- v0.67.0 r1 (`670001`): `798c27251aa58c077221fb73f8728e688173430a13acd26b1fcc7f35e346e7fb`
- v0.66.0 r2 (`660002`): `4fd49f95a78eba6d9dfac96ab1ecab42cbe60ca24cff1877dea941cf299fe3bd`
- v0.66.0 r1 (`660001`): `cf9f6de24180ec12e2368be29ef4296831107bd5a5b007ec7aa55cd1ebf8c8b1`
- v0.65.0 r1 (`650001`): `fdb6d301af07397b5a8381831f963daef065db2b7d3dd40c714a3ced5f1c74c7`
- v0.64.0 r1 (`640001`): `734f5670672558d10f775b008b4f69b57a03fb5a928b196cfcfcb468a3c36389`
- v0.63.1 r1 (`631001`): `1a517ad54d9dc58f180f5126f3cf661764ee66bed2b12c4978a349b8d436264b`
- v0.63.0 r1 (`630001`): `84ff5841e5c4dbec5a11e24c80115d0b23a9f2bd0838843d3255ae3755427492`
- v0.62.0 r1 (`620001`): `036044ac4944f7839325b9d95fdba7bb86bb5ed1b73d34feda4ef9e8688827e1`
- v0.61.0 r1 (`610001`): `cc52c84aa519d72de65465ed39224043ef8e1cd044dc6abda013c1e27882f705`
- v0.60.0 r7 (`600007`): `562d28d63902f86e55079d56120c30398dbbbd03914d19bf1328d41d48747f96`

## Phone coverage

I test on one physical phone, a Samsung device running One UI on Android 16.
I only re-test on the phone when a release makes significant app changes, such
as Pages widget work or anything touching networking, lifecycle or Live
sessions. Compatibility releases that add fixtures and presentation rules rely
on the emulator and CI checks above, and I deliberately skipped the phone check
for v0.63.0.

What I have confirmed on the phone:

- **In-place upgrades:** v0.60.0 r7, v0.61.0 r1, v0.62.0 r1 and v0.66.0 r1 each
  installed over the previous build without uninstalling and kept the placed
  Pages widget. The last three also kept the saved pairing, with no
  re-pairing.
- **In-app update path:** I updated from v0.65.0 to v0.66.0 r1 through
  **Settings → App updates** and Android's user-confirmed installer. The
  dashboard reconnected, the widget refreshed, and no Token Monitor service
  was left running. I updated from v0.77.0 r1 to r2 the same way; the Pages
  widget redrew without opening the app. A USB install, an install followed by
  force-stop, and an install followed by a forced recompile also redrew it, so
  I closed [issue #60](https://github.com/The-Minion-oOo/token-monitor-android/issues/60).
  I didn't check pairing, Live or the update frequency in that r2 phone test.
- **Widget layout and fonts:** One UI substitutes its own monospace face, and
  phone captures with real multi-tool, multi-model data showed crowded and
  overlapping rows that a two-row emulator fixture had hidden. That is why the
  app bundles a JetBrains Mono subset and the widget is drawn from
  [`WIDGET_SPEC.md`](WIDGET_SPEC.md), checked against a dense fixture. Earlier
  phone inspection also showed that Samsung's stack presentation fanned the
  cards and clipped child views, so each page is now drawn as one bitmap.
- **Battery:** an early measurement was a 25-minute screen-off Live session on
  an early build. Android attributed about 1.54 mAh to the app, but the session
  received 104.19 MB and posted 698 notification updates, which led to the
  30-second stats refresh and notification deduplication. Idle-state checks on
  later candidates found no active Token Monitor service or wake lock. A large
  battery drain I saw on the phone came from a UI-test helper I had left
  installed, not from Token Monitor; I removed it.
  On 2026-10-21 I compared Widget Live with the screen off for about seven
  minutes per build: v0.76.0 r1 against a local build of the performance
  changes later released in v0.77.0 r1. Android attributed 1.34 s of app CPU
  and an estimated 0.084 mAh to v0.76.0 r1, and 0.86 s and 0.050 mAh to the
  local build. That is one short run each, and the mAh figure is Android's
  estimate rather than measured current.

The published upgrade checks above apply to the named releases. Later local
performance and widget builds were also checked on my phone, but those results
do not establish upgrade, widget or battery behavior for the v0.77.0 release.
I didn't phone-test v0.77.0 r1. For r2 I checked only the in-app update and the
Pages widget redraw described above.

## Known limits

- The synthetic performance test measures cache hydration and processing on an
  API 36 emulator, not a true process or filesystem cold start. In the
  v0.66.0 r2 baseline, a 2,029,902-byte stats payload (3,522 month sessions)
  had a median warm cache hydration of 169.9 ms and a median freshness update
  of 264.9 ms. These are local observations, not timing guarantees. Widget
  timings cover off-screen bitmap drawing only.
- Battery use has not been measured for the published v0.77.0, v0.78.0, v0.79.0, v0.80.0 or v0.81.0 APKs. The short
  Widget Live comparison above used a local build of the same performance
  changes; it is not a measurement of the published APK.
- API 26 is a smoke check of install, launch and resume, not full interaction
  or background coverage.
- Tailscale and Wi-Fi route switching, screen-off Live expiry and One UI
  rendering are not covered by emulator tests. I checked the local-network
  permission prompt and its denial and restore flows on an API 37 emulator,
  which does not establish Tailscale or One UI behavior.
