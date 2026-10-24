# Background widget refresh (unreleased)

I added an optional way to refresh placed widgets while the app is closed.
It starts Off, with Wi-Fi only enabled. The interval choices are 30 or 60 minutes;
Android may delay a run while the phone sleeps.

## What changed

- One quiet, bounded JobScheduler job, without a new dependency.
- Cancellation without widgets, pairing or consent, or after secret rejection.
- Active dashboard and widget sessions take priority; only widget read routes are fetched.
- Saved figures remain on failure, with the existing STALE status.
- Widget settings show response-body traffic and a daily estimate.

Tailscale remains recommended and the internet Hub route stays off by default.
[Widget guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/WIDGETS.md).

## Testing

This feature has not been phone-tested. I skipped the overnight phone battery
and data check; real overnight battery use, traffic and physical-device reboot
behavior remain unverified. The light/dark settings and recaptured widget
comparison are approved.

Local checks passed: 301 JVM tests with the generated Hub contract, lint, debug
assembly and 14 tooling tests. The 84-test suites passed on API 36 and API 37,
followed by 21 focused regressions on each after the final stale-state fixes.
The pinned Node and Worker Hub contracts passed locally with synthetic data.
These are local results; CI is tracked in the feature PR.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
