# Contributing

Thanks for looking at this. The app is small on purpose, and the rules below
keep it that way.

## Before you start

- Read [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) for the toolchain,
  commands, and conventions, and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
  for how the pieces fit.
- The app is a read-only dashboard for the desktop Token Monitor Hub. Changes
  that add collection, remote control or telemetry are outside scope. The
  existing widget foreground service is opt-in and bounded; changes to its
  lifecycle or permissions require explicit review. Changes that widen the network boundary need
  a clear reason in the pull request.
- The desktop widget is the design reference. If you are changing how
  something looks or reads, compare it with the desktop first and say so in
  the pull request.

## Workflow

1. Branch from `main` with a short task-based name (`text-size`,
   `settings-sections`).
2. Make the change with tests where a pure function is involved.
3. Run the same checks CI runs:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ```

4. Exercise the change on an emulator or phone. For visual changes, capture a
   screenshot from the fixture Hub, never from a real Hub.
5. Open a pull request against `main`. Describe what changed and why, what you
   ran, and attach the screenshot for UI work. CI must be green before merge.
6. Squash-merge. The pull request title becomes the commit subject.

## Commit messages and pull requests

Write them plainly: a short imperative subject such as
`Add an optional home Wi-Fi fallback address`, and a body only when the diff
does not explain the why. No ticket jargon, no severity labels, no generated
trailers or footers.

## Never commit

- Hub secrets, Tailscale addresses, account emails, device names, or raw Hub
  responses. Use the sanitized fixtures under `app/src/test/resources`.
- Keystores, signing passwords, or scripts that contain them.
- Screenshots taken from a real Hub. The README gallery uses the showcase
  fixture only.
- Build output, IDE files, or `local.properties`.

## Reporting problems

Open an issue with the app version (Settings → App updates), the desktop
Token Monitor version, what you expected, and what happened. Reproduce with
the fixture Hub when you can. Do not paste a Hub secret, an unredacted account
email, or a private network address into an issue.
