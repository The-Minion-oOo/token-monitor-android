# Keeping pace with desktop Token Monitor

## Version rule

The Android app displays `v0.56.0` when it is verified against desktop Token Monitor `v0.56.0`. The Gradle properties and `upstream.json` identify tag `v0.56.0` and peeled commit `2f60827e3028d283969dd74cde5b3f5664220442`.

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
4. Update the protocol DTO/parser only where the wire contract changed. Keep prior fixture tests when backward compatibility is expected.
5. Compare Android screens with the new upstream screenshots and renderer tokens.
6. Update `upstream.json`, Gradle compatibility fields, README compatibility table, and validation evidence together.
7. Run unit tests, lint, debug/release builds, emulator interaction checks, and the physical-phone gate before publishing.

The scheduled check reports a release; it never merges upstream code automatically. Desktop renderer code cannot be copied wholesale into a native Android app, and protocol or privacy changes require review.

## Why updates stay contained

The wire adapter, domain model, and UI are separate. Additive fields are ignored safely. New useful fields are mapped once in the parser and then exposed through stable domain types. Most upstream releases should therefore require fixture and adapter work rather than a dashboard rewrite.
