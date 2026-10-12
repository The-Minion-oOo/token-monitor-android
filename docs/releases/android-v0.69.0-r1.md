# Token Monitor for Android v0.69.0 r1

This release follows desktop Token Monitor v0.69.0 and uses its new session
information to make busy sessions easier to read and to show which ones are
waiting for you.

## What changed

- Codex subagents and continuation files are grouped under their main session
  with combined usage, as are T3 Code continuations and delegated runs. Each
  session keeps its own usage, and the totals are unchanged.
- Cursor Grok Bot activity records appear as one group, and Codex background
  reviews are gathered as **Codex Auto Review**.
- Claude Code and Codex sessions that are waiting on a reply or approval show
  **Waiting for you** in Home and Sessions. A fresh provider observation takes
  priority; otherwise the app falls back to the session's recent transcript
  activity, as before.

Question text, answers and prompts never leave the desktop, and the app still
only reads. The fixed-aspect Pages widget, widget coordinates, Wi-Fi fallback,
bounded Live sessions and release signing identity are unchanged. Collecting
session activity stays on desktop.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.69.0, with earlier Hub
fixtures retained. With an older Hub, subagents and continuations stay separate
rows because the links they need are missing. Package `io.github.theminionooo.tokenmonitor`, version code
`690001`, signed with the existing release certificate. Use **Settings → App
updates** or install the APK over your current release. Do not uninstall first.
See [Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

Verification includes JVM tests, lint, emulator checks and the released Hub
contract. Build and emulator evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
I haven't installed this release on my phone yet.
