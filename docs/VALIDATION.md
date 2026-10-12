# Validation

This page records how I check each Android release and what those checks do and
do not cover. I report emulator and CI results as exactly that, and I never
describe them as results from a physical phone. Phone evidence applies only to
the build it names.

## How each release is checked

Every release goes through the same automated gate before I publish it:

- **Unit tests and builds:** 153 JVM tests, run with the generated Hub contract
  supplied and nothing skipped, plus Android lint, the preview debug build and
  the instrumentation APK.
- **Instrumentation suite:** 46 tests on API 36 and API 37 emulators. A report
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

## Current release: v0.69.0 r1

The desktop baseline is tag `v0.69.0`, commit
`3f106ae3d4712b26cced8272f0d488eaab0de1e6`. I reviewed its release notes, API
changes and session presentation at that pin. Android now reads session identity,
parent links and live activity, groups subagents, continuation files, T3 Code
runs, Grok Bot records and background reviews the way the desktop does, and shows
when a session is waiting for a reply.

The released-Hub contract check passed against the pinned v0.69.0 Hub, including
the new session fields and the privacy sentinels. All JVM tests, lint and the
debug build passed, and CI ran all 46 instrumentation tests on API 36 and API 37
plus the optimized API 26 smoke check. I compared emulator captures of Home and
Sessions, with a group collapsed and expanded, before merging.

- APK: `token-monitor-android-v0.69.0-r1.apk`, 2,467,030 bytes
- Package: `io.github.theminionooo.tokenmonitor`, version code `690001`
- File SHA-256:
  `b188411aa93abc478802dd7f5e33d7a0e6653cdbf7ada2dba38057dee174f3cd`
- Certificate SHA-256:
  `eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`

The release tag points to `main` commit
`c972f3c7c95eceb38490bffb48764920802a8e88`, merged through
[PR #44](https://github.com/The-Minion-oOo/token-monitor-android/pull/44).
The required checks passed on that exact push:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/38196574552),
[Android interaction and optimized API 26 smoke checks](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/38196574513),
and [released Hub contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/38196574524).
I verified the APK, checksum and update manifest on the draft and again after
downloading them from the public release,
[android-v0.69.0-r1](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.69.0-r1).
It was published on 2026-10-12 at 04:28 UTC. I haven't installed this release on my phone
yet.

## Release history

Each row is a published release. The phone column says whether I installed that
build on my phone. Builds before v0.60.0 r7 are recorded in the
[changelog](../CHANGELOG.md). v0.60.0 r5 and r6 were unpublished candidates.

| Release | Date | Desktop baseline | Main checks | Phone check |
| --- | --- | --- | --- | --- |
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

APK SHA-256 for each published release (v0.69.0 r1 is listed above). All of
them use package `io.github.theminionooo.tokenmonitor` and the same signing
certificate, SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.

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
  was left running.
- **Widget layout and fonts:** One UI substitutes its own monospace face, and
  phone captures with real multi-tool, multi-model data showed crowded and
  overlapping rows that a two-row emulator fixture had hidden. That is why the
  app bundles a JetBrains Mono subset and the widget is drawn from
  [`WIDGET_SPEC.md`](WIDGET_SPEC.md), checked against a dense fixture. Earlier
  phone inspection also showed that Samsung's stack presentation fanned the
  cards and clipped child views, so each page is now drawn as one bitmap.
- **Battery:** my only measurement was a 25-minute screen-off Live session on
  an early build. Android attributed about 1.54 mAh to the app, but the session
  received 104.19 MB and posted 698 notification updates, which led to the
  30-second stats refresh and notification deduplication. Idle-state checks on
  later candidates found no active Token Monitor service or wake lock. A large
  battery drain I saw on the phone came from a UI-test helper I had left
  installed, not from Token Monitor; I removed it.

The last build I tested on the phone is v0.66.0 r1 (`660001`). v0.66.0 r2,
v0.67.0 r1, v0.68.0 r1, v0.68.0 r2 and v0.69.0 r1 have not been rechecked on a
phone, and neither were v0.63.0 through v0.65.0 when they were released.

## Known limits

- The synthetic performance test measures cache hydration and processing on an
  API 36 emulator, not a true process or filesystem cold start. In the
  v0.66.0 r2 baseline, a 2,029,902-byte stats payload (3,522 month sessions)
  had a median warm cache hydration of 169.9 ms and a median freshness update
  of 264.9 ms. These are local observations, not timing guarantees. Widget
  timings cover off-screen bitmap drawing only.
- Battery use is not measured on current builds. The only measurement and the
  idle-state checks are described under Phone coverage.
- API 26 is a smoke check of install, launch and resume, not full interaction
  or background coverage.
- Tailscale and Wi-Fi route switching, screen-off Live expiry and One UI
  rendering are not covered by emulator tests. I checked the local-network
  permission prompt and its denial and restore flows on an API 37 emulator,
  which does not establish Tailscale or One UI behavior.
