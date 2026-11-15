---
name: Android release
about: Track a desktop compatibility update or Android-only release
title: "[Release] "
labels: release
---

## Summary

<!-- Name the planned Android version and revision, the desktop version it supports, and what changes for users. -->

## Release steps

- [ ] Confirm the desktop source pin and prepare the Android changes, version and release notes.
- [ ] Merge the release code PR after its checks pass.
- [ ] Build and sign the APK from the verified main commit.
- [ ] Publish the verified APK, checksum and update manifest.
- [ ] Merge the release documentation and record the published release.

## Testing

- [ ] Released-source contract checks and versioned JVM fixtures.
- [ ] Unit tests, lint, debug/release builds and documentation/tooling checks.
- [ ] Native presentation checks and the required API 36/37 and API 26 jobs.
- [ ] Full diff review and validation of any fixes.
- [ ] All required jobs pass on the exact merged release source.
- [ ] Draft and anonymous public downloads match the signed APK, checksum and update manifest.

<!-- Add a separate phone checkbox when required. Record the exact APK and what was tested, or state the limits of coverage. Leave pending checks unchecked. Link the CI runs, merged PRs and published release as they become available. -->
