# Keeping pace with desktop Token Monitor

## Version rule

Android v0.80.0 r1 targets desktop tag `v0.80.0`, peeled commit
`c61cbe998d69725589c03511f2f56d43714f4c0f`, in Gradle and `upstream.json`.
I reviewed the Muse Code quota collector, Ollama API/Cookie credits and Claude
managed accounts against the released Node and Worker Hubs. Muse readings stay
separate by device. Credit amounts and monthly spend retain their currency, and
absent quotas stay unknown. Credential storage, login renewal and account
management remain on desktop, along with Notch Panel changes.

The twelve languages, payment/provider details and display controls from
v0.79.0 r2 are retained. Android still uses the five-second complete-update limit.
Published release identity stays separate from candidate metadata in `release.json`.

The latest published release is
[`android-v0.80.0-r1`](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.80.0-r1),
recorded with its source commit in `release.json`.
It retains opt-in HTTPS, optional background widget refresh and the
r5 physical-network check underneath Tailscale. Earlier phone evidence applies
only to the builds named in [Validation](VALIDATION.md).

An Android-only fix increments `versionCode` and the GitHub release revision,
for example `android-vX.Y.Z-rN`, without changing the visible compatibility
version. A newly verified desktop release updates the visible version to match
it and starts again at release revision 1.

## Update procedure

1. Run `tools/check-upstream.ps1` or the scheduled GitHub workflow.
2. Read the upstream release notes and diff these areas first:
   - `docs/API.md`
   - `src/hub/server.js`
   - `src/shared/history.js`
   - `src/shared/usage.js`
   - `src/electron/renderer` view and presentation helpers
3. Save sanitized responses for every Android read endpoint under a new `app/src/test/resources/protocol/vX.Y.Z` directory.
4. Sort each change into one of four contained lanes:
   - stable wire fields in `HubDtos` and `HubProtocolParser`;
   - stream event delivery and freshness merging in `HubStreamProtocol`;
   - reusable display semantics in focused presentation helpers such as
     `SessionPresentation` and `VendorPresentation`;
   - native Android layout changes only when the new data improves a mobile view.
5. Keep prior fixture tests when backward compatibility is expected. An omitted
   optional field must retain its previous behavior.
6. Compare Android screens with the new upstream screenshots and renderer tokens.
   Desktop-only window features do not become Android work by default.
7. Update `upstream.json`, Gradle compatibility fields, README compatibility table,
   candidate release notes, and validation evidence together.
8. Run unit tests, lint, debug/release builds and emulator interaction checks.
   Use the phone gate for significant networking, lifecycle or widget changes;
   otherwise say in the release notes that I haven't phone-tested the build.

The scheduled check reports a release and closes obsolete automated review
reminders already covered by the verified baseline. It preserves manual issues
and closed review decisions, and never merges upstream code automatically.
The pinned-source contract workflow also exercises the released Hub with
synthetic data and passes its responses through Android's parser; see
[Development](DEVELOPMENT.md). Desktop renderer code cannot be copied wholesale
into a native Android app, and protocol or privacy changes require review.

## Why updates stay contained

The wire adapter, stream reducer, domain model, and presentation rules are separate.
Additive fields are ignored safely. New useful fields are mapped once in the parser,
incremental transport events are merged without teaching screens about SSE, and UI
semantics are tested without Compose. Most upstream releases should therefore require
a versioned fixture plus small adapter changes rather than a dashboard rewrite.
