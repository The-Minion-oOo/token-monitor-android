# Android v0.78.0 r2

I added an optional internet Hub connection over HTTPS. Tailscale remains the
recommended setup, and the new switch starts off for both new and existing
pairings.

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

## Testing

I checked the pinned v0.78.0 Node and Worker Hubs locally with synthetic data,
including authenticated reads and stream-v2 snapshot, stats and freshness events.
All required checks passed on the released main commit: build, API 36/37
interaction suites, optimized API 26 smoke and the released-Hub contract.
Local checks passed 294 JVM tests, 73 API 37 emulator tests, lint, signed release
assembly and 14 tooling tests. I verified the APK package, version, certificate,
checksum and update manifest, and reviewed the full diff.

On my phone, the three targeted UI/storage checks passed. The preview app refused
public HTTP and internet HTTPS without opt-in. HTTPS snapshot, freshness and
changed-stats checks passed against a disposable synthetic Hub over cellular,
with Wi-Fi and VPN off. I watched the totals change without refreshing, then
closed the endpoint and removed the disposable pairing.

I installed the signed r2 APK over r1 on my phone. The saved main and home
addresses survived, the Hub reconnected without re-entering its secret, and
internet access stayed off. Tailscale and the home Wi-Fi fallback both connected.
I restored the original Wi-Fi/VPN state. Widget expiry, battery use and long-term
connection reliability were not remeasured for this revision.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
