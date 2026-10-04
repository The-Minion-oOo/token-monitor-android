# Token Monitor for Android v0.66.0 r2

This candidate improves home Wi-Fi recovery, widget session limits and the
accuracy of existing dashboard views. The desktop baseline stays v0.66.0.

## What changed

- Android 17 requests local-network access when needed, explains a denied
  permission and lets a configured Tailscale route remain usable.
- A changed home address can be tested and saved with the existing pairing.
  Discovery fills the form; only a successful authenticated check saves it.
- Widget Live includes sleep in its one-hour limit and checks the deadline
  before requests. Offline notifications show reconnecting and saved-data age.
- Trends keeps missing days as gaps and includes usage without tool/model
  attribution. Session search respects title visibility and matches identifiers
  and tool names. Empty periods and searches have separate explanations.
- Selected controls, charts and widget pages expose clearer accessibility
  summaries. Home and Limits use the same quota urgency rules.
- Live updates avoid a redundant stats decode. Compatibility and release checks
  now include pinned released Hub source, multiple Android versions and matching
  APK, checksum, update-manifest and signing identities.

The fixed Pages widget composition, private read-only Hub connection and existing
release certificate are retained.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.66.0. Package
`io.github.theminionooo.tokenmonitor`, version code `660002`.
This is an unpublished candidate. The public release remains v0.66.0 r1.

Build, emulator, review and performance evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
The r1 phone update preserved pairing and the placed Pages widget; r2 has not
been installed on that phone. Its in-place upgrade, Tailscale/Wi-Fi transitions,
screen-off expiry and battery behavior remain physical-device release checks.
See [installing and updating](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
