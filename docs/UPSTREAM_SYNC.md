# Keeping pace with desktop Token Monitor

## Version rule

Android v0.78.0 r1 targets desktop tag `v0.78.0`, peeled commit
`d52e0b9cf970a03a30a5f5a1e9139693bb211cd8`, in Gradle and `upstream.json`.
I reviewed the changes since v0.77.0. ZCode's saved titles and tool/workflow
parent links pass through the existing parser and session grouping. Forks and
side chats keep separate rows. Released-source checks cover title sharing off,
on and revoked, without sending conversation bodies. Desktop glass, tooltip
and CLI-probe changes remain desktop internals. Android keeps the v0.77.0 r2
performance improvements, five-second complete-update limit, widgets, animations,
GET endpoints and retry policy. Title-sharing controls remain on desktop.

v0.78.0 r1 is a candidate until the signed release is published.

The latest published release is
[`android-v0.77.0-r2`](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.77.0-r2),
recorded with its source commit in `release.json`. Its performance and widget
changes have emulator coverage. On my phone, the in-app update from r1 redrew
the Pages widget without opening the app.

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
