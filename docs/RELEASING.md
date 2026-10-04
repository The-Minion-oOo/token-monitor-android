# Releasing

## Versioning

The visible Android version matches the verified desktop Token Monitor version.
Android-only releases keep `versionName` and increment the final three digits of
`versionCode` and the GitHub tag revision.

| Build | Version name | Version code | Tag |
| --- | --- | --- | --- |
| Published | v0.66.0 | 660002 | `android-v0.66.0-r2` |

A newly verified desktop version updates the visible version and starts its
Android revision at 1. `release.json` records the working build, last published
release, production package/certificate, and required CI jobs.
`tools/check-release.mjs` checks it against `gradle.properties`, `upstream.json`,
and the installer's pinned identity. Keep README and protocol fixtures aligned
with those records.

Keep `published` at the last public release while a new build is a candidate or
draft. After publication, record the actual released tag, version, and tagged source commit in `published`
and set `candidate.status` to `published` with the same build identity. This
records the release without inventing the next Android revision. New app work
sets the status back to `candidate` and uses a higher version code. A published
record cannot be prepared as another draft.

## Release checks

Before publishing:

- Update `release.json` and Gradle version metadata, then add concise notes under
  `docs/releases/`. Keep the verified upstream pin unchanged for Android-only work.
- Update README compatibility and widget marketing from production captures;
  do not replace the established hero for a widget-only release.
- Run JVM tests, lint, debug assembly, and release assembly.
- Wait for every required job in `release.json` on the exact `main` commit being
  released: `build`, `interaction-api36`, `interaction-api37`, `optimized-api26`,
  and `contract`.
- Exercise affected dashboard and widget behavior on the emulator.
- Install the signed candidate over the previous release on a phone and confirm
  pairing and preferences survive.
- For networking, lifecycle, or widget changes, check Tailscale, Wi-Fi fallback,
  offline resume, Live/Stop/expiry, and the affected widget sizes on the phone.
- Confirm the working tree contains no keystore, passwords, private addresses,
  personal data, captures, or raw Hub responses.
- Perform an independent read-only review of the release diff and fix validated
  findings.

Record what was actually checked in [Validation](VALIDATION.md). Do not describe
emulator results as phone coverage or design safeguards as measured battery life.
The API 26 optimized check covers installation, launch, and resume; it is not the
full interaction suite. The synthetic performance test measures cache hydration
and processing, not a true cold start. See [Development](DEVELOPMENT.md) for the
matrix, real-Hub contract harness, and measurement limits.

## Signing

Release builds read four environment variables:

```text
ANDROID_KEYSTORE_FILE
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The keystore and passwords stay outside the repository. Keep an encrypted backup
of the keystore and record the certificate SHA-256 fingerprint separately; losing
the key prevents future APKs from updating installed copies.

After signing, inspect the APK and prepare its release assets:

```powershell
node tools/check-release.mjs --apk app/build/outputs/apk/release/app-release.apk --prepare build/release
```

Set `ANDROID_SDK_ROOT` or `ANDROID_HOME` to the installed SDK. The tool uses its
build tools to verify the signature and read the APK manifest. It requires the
production package, expected version, and existing signing certificate before
writing the APK, checksum, and `token-monitor-android-update.json` together.
It then reads the assets back and checks their size/hash agreement. Recheck a
downloaded release bundle with `--apk <path-to-named-apk>` without `--prepare`.

## Publishing

The manually triggered **Android release** workflow runs only from `main`.
Before restoring signing material, `tools/release-preflight.mjs` confirms that
the release SHA is still current `main`, the latest published tag matches
`release.json`, and every required workflow/job passed on that exact main push.
Missing, skipped, failed, or incomplete jobs block preparation. An older green
run does not replace a newer failed attempt.

The workflow runs JVM tests and lint, builds the signed APK, and prepares the
verified three-asset bundle. It repeats the main/SHA checks before opening a
draft GitHub release with notes from
`docs/releases/android-v<version>-r<revision>.md`. It does not publish the draft
or overwrite an existing tag. Signing material is removed when the job ends.
The four signing secrets must be configured in the repository; the keystore
itself stays outside Git.

For a local release, use the same signing key and asset preparation command.
With a GitHub token available in `GH_TOKEN`, run the same read-only preflight
against the reviewed main commit before creating the draft:

```powershell
node tools/release-preflight.mjs (git rev-parse HEAD)
```

The published APK, checksum, and update manifest must be the verified files.
Download the uploaded assets again and check the bundle before publication.

Check that the manifest's version code, APK name, size, and hash match the
uploaded APK. Review the draft, install its exact APK on the phone, then publish
it. Release notes should state what changed, compatibility, and any important update action;
link to [Install and update](INSTALL.md) instead of repeating the full procedure.

Batch related work and publish when the APK is worth reinstalling. Documentation,
marketing-image placement, and other non-app changes do not need a new Android
revision.
