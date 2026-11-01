# Android v0.83.0 r2

I shortened the wait before Token Monitor tries a saved home address when its
first Tailscale connection stalls.

## What's new

- **Quicker home fallback:** the first dashboard health connection gets a
  two-second TCP timeout when Wi-Fi or Ethernet is available and a permitted
  plain-HTTP home address is saved. Both addresses must already pass the route
  checks. The Hub identity is verified before any authenticated reads.
- **A narrow startup change:** mobile-only and HTTPS connections, pairing,
  widgets and later retries keep their existing timeouts. Established response
  and body reads still have 35 seconds to finish.
- **Optional startup diagnostics:** the development build option now identifies
  lifecycle changes, connection runs and retry waits using fixed labels and
  numbers. It is off in the published APK.

Desktop compatibility remains v0.83.0. This revision uses the existing dashboard,
widgets, languages and animations.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. The version code is **830002**, signed with the existing
release key. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I compared matched diagnostic builds on my phone after unplugged idle waits.
Both refreshed normally over Tailscale: 1.12 seconds before the change and
1.14 seconds with it. The intermittent stalled connection did not recur, so
those readings do not verify a speed improvement. A stalled-connect emulator
fixture checks the shorter timeout and successful home fallback directly.
Further investigation remains in
[#115](https://github.com/The-Minion-oOo/token-monitor-android/issues/115).
Battery savings have not been measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).

I haven't phone-tested the final signed r2 APK. The comparison above used signed
diagnostic builds. The exact final APK passed the optimized Android 8 install,
launch and resume check.
