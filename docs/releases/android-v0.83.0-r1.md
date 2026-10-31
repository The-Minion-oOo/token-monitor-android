# Android v0.83.0 r1

I updated the Android companion for desktop Token Monitor v0.83.0.

## What's new

- **Claude plans and quotas:** Max and Ultra appear with the reported account
  details. Weekly-only CLI quotas display without an invented session allowance;
  a reported zero still means exhausted.
- **Trae CN credits:** Android reads the credit summary supplied by the desktop,
  including readings obtained through its new browser-session option. Credentials
  stay on desktop, and a missing percentage stays unknown.
- **Initial dashboard refresh:** independent Hub reads use at most two concurrent
  requests after identity verification. Fresh data appears only after the complete
  snapshot is ready. The intermittent Tailscale startup stall remains under
  investigation in [#115](https://github.com/The-Minion-oOo/token-monitor-android/issues/115);
  the newer failover fix is not included.

The existing languages, widgets, animations and update frequency are retained.
Proxy configuration, browser sign-in and archive scanning remain desktop features.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I haven't phone-tested the final v0.83 APK. The earlier startup comparison applies
to the code checked in PR #116; it does not establish battery savings or resolve
the remaining stall. Further phone checks are deferred.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
