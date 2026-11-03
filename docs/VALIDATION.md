# Validation

I keep one record for each release. Emulator results and phone checks describe
the builds and scenarios they name; neither establishes battery savings.

## Current release: v0.85.0 r1

I published source `52531a964c920429b224a21ebb9616d816e6ed69` from
[PR #130](https://github.com/The-Minion-oOo/token-monitor-android/pull/130),
with desktop v0.85.0 pinned at `d7fd7db7eb13c912acf0a94b07f5b2baa64149ba`.
All five required jobs passed on that exact main commit:
[build](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/41672395527),
[API 36/37 and optimized API 26](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/41672395619)
and [released Hub/Worker contract](https://github.com/The-Minion-oOo/token-monitor-android/actions/runs/41672395525).

Checks passed **443 JVM tests**, **130 native tests on each CI API 36/37 emulator**,
debug/release lint, preview/debug/test and signed R8 builds, 14 tooling tests,
twelve language labels, documentation and release metadata. Reports have no
failures or skips. The full local API 36 suite also passed, along with the four
focused tests on each local API 36/37 emulator. I reviewed the full diff and
inspected filter/group and Devices/Home hardware captures. The exact final
signed APK passed the API 26 install/launch/resume check.

The APK is `token-monitor-android-v0.85.0-r1.apk`, **3,404,794 bytes**,
package `io.github.theminionooo.tokenmonitor`, version code **850001**.
SHA-256: `c3328752f40118561c5d802a6169c0f5361381aef18b0d4623526e4d7d63cd8e`.
The signing certificate is
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
Draft and anonymous public downloads matched the APK, checksum and update
manifest; anonymous Latest discovery identified r1.
[Android v0.85.0 r1](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.85.0-r1) is Latest.

Released-source cases cover hardware kinds, older updates, generic resets and
Claude title priority, rename/removal and off/on/revoked consent using only a
synthetic scoped home. Android checks cover whole mixed-tool groups, unchanged
totals, search combinations, missing selections and hidden-title search. The
hidden-group match was reproduced before fixing it. Widget geometry and
connection ownership are unchanged; no session origin is inferred.
I haven't phone-tested this release or measured battery use. Earlier phone
results apply only to their named builds. Follow-ups remain in
[#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94) and
[#115](https://github.com/The-Minion-oOo/token-monitor-android/issues/115).

## Release checks and limits

Every release requires the `build`, `interaction-api36`, `interaction-api37`,
`optimized-api26` and `contract` jobs on the exact tagged main commit, plus the
local signing and bundle checks in [Releasing](RELEASING.md). The contract uses
isolated synthetic data and the actual pinned Node Hub and Worker. No private
Hub or provider account is used by CI.

API 26 covers optimized installation, launch and resume, rather than the full
interaction suite. Emulator timings measure their fixture workload; they do not
measure phone battery, VPN handover, One UI rendering or full cold-start latency.
Phone results apply only to the named build and scenario. See
[Development](DEVELOPMENT.md) for the test matrix and synthetic measurements.

## Release history

Each row is a published release. The phone column says whether I installed that
build on my phone. Builds before v0.60.0 r7 are recorded in the
[changelog](../CHANGELOG.md). v0.60.0 r5 and r6 were unpublished candidates.

| Release | Date | Desktop baseline | Main checks | Phone check |
| --- | --- | --- | --- | --- |
| v0.85.0 r1 | 2026-11-03 | v0.85.0 | Session tool filter, hardware hints and title privacy; 443 JVM / 130 per API 36/37; optimized API 26 | Not phone-tested |
| v0.84.1 r1 | 2026-11-02 | v0.84.1 | Kiro quota recovery and Copilot reset compatibility; 428 JVM / 126 per API 36/37; optimized API 26 | Not phone-tested |
| v0.84.0 r1 | 2026-11-01 | v0.84.0 | Manus credits, Muse web/CLI products, throughput compatibility; 421 JVM / 124 per API 36/37; optimized API 26 | Not phone-tested; prior r2 phone results apply only to that build |
| v0.83.0 r2 | 2026-10-31 | v0.83.0 | Initial home fallback timeout; 409 JVM / 121 tests per API 36/37; optimized API 26 | Yes: Home Wi-Fi refresh/resume after updating, saved pairing and matching APK; Tailscale idle/failover pending |
| v0.83.0 r1 | 2026-10-31 | v0.83.0 | Claude plans/weekly-only quotas, Trae credits and bounded startup reads; 397 JVM / 112 tests per API 36/37; optimized API 26 | Final APK not phone-tested; further #115 checks deferred |
| v0.82.0 r1 | 2026-10-30 | v0.82.0 | Archive corrections/model evidence and Claude-only expiry estimates; 377 JVM / 105 tests per API 36/37; optimized API 26 | Not phone-tested; battery unmeasured |
| v0.81.0 r2 | 2026-10-29 | v0.81.0 | Hub route/stream/job hardening; 370 JVM / 103 tests per API 36/37; optimized API 26 | Yes: exact signed APK upgrade, Pages redraw, dashboard/Live/forced periodic on both routes and offline recovery; battery unmeasured |
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

Earlier APK checksums and detailed validation records remain in their tagged
release assets and the Git history of this page.
