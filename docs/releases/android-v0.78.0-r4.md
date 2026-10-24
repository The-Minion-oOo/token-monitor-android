# Android v0.78.0 r4

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

## Compatibility and updating

Desktop compatibility remains v0.78.0. Install over the existing app to keep pairing
and preferences; background refresh starts Off after upgrading.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

## Testing

Unit tests, lint, debug assembly and emulator checks passed on the feature commit.
The pinned Node and Worker Hub contracts passed locally with synthetic data.
Light/dark settings and the recaptured widget comparison are approved.

An overnight preview check on my phone completed eight background runs, received
about 2.09 MB of response data and recorded 8.229 seconds of total job time.
Android attributed an estimated 1.12 mAh to the preview app across the capture
interval. That estimate is not a direct measurement of the feature's incremental
battery cost. A matching refresh-Off baseline remains an optional follow-up in
[issue #76](https://github.com/The-Minion-oOo/token-monitor-android/issues/76).

The hourly Wi-Fi-only job returned after a physical reboot without opening the
preview app, and a bounded post-reboot read succeeded. The signed r2-to-r4
upgrade retained pairing, home Wi-Fi connectivity and display settings.
Background refresh remained Off with Wi-Fi only on. The preview test app and
its widget were removed after checking.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
