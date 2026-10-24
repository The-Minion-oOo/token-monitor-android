# Android v0.78.0 r2 — candidate

I added an optional internet Hub connection over HTTPS. Tailscale remains the
recommended setup, and the new switch starts off for both new and existing
pairings. This candidate has not been published.

## What's new

- **Allow an internet Hub over HTTPS** connects to an existing Cloudflare Worker
  Hub or a Node Hub behind an HTTPS reverse proxy using a public DNS name.
- A bare name gains HTTPS and port 443. Public HTTP, IP literals and private-use
  names are refused. Certificate and hostname failures have clear messages.
- The pairing screen explains that the Hub secret can also change Hub data.
  Use a long random secret and change it on the Hub and every desktop if the
  phone is lost.

The switch does not expose a computer, open a port or deploy a Hub. Home Wi-Fi
fallback, widget controls and the installed app's update source remain unchanged.
[Pairing](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/PAIRING.md#optional-internet-hub-over-https).

## Verification and release gate

I exercised the pinned v0.78.0 Node and Worker Hub implementations locally with
synthetic data, including authenticated reads and stream-v2 snapshot, stats and
freshness events. Those contract checks contacted no deployed Hub. The local
checks passed 294 JVM tests, 73 API 37
emulator tests, lint, preview debug builds, optimized unsigned release assembly
and 14 tooling tests. I reviewed the full diff. CI on the merged commit and
signed release assembly are still pending. Before
publication I still need the signed in-place upgrade and retained-pairing check,
plus the Tailscale/home-fallback regression checks in the release checklist.

The three targeted UI/storage checks passed on my phone. The preview app refused
public HTTP and internet HTTPS without opt-in. HTTPS snapshot and live SSE checks
passed against a disposable synthetic Hub over cellular, with Wi-Fi and VPN off.
Two production-repository probes verified freshness and changed-stats events;
I also watched the dashboard totals change without refreshing. I closed the
temporary endpoint and removed the disposable pairing. My production app and
pairing were unchanged. Battery use and long-term reliability were not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
