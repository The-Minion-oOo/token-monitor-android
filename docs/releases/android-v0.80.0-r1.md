# Android v0.80.0 r1

I updated Android for desktop Token Monitor v0.80.0, with Muse Code quotas,
Ollama credits and multiple Claude Web accounts.

## What's new

- **Muse Code limits:** session and weekly quotas appear with the reported plan.
  Readings from different desktops stay separate because Muse supplies no shared
  account ID. Use Muse Code 1.4.13 or newer, sign in with `muse login`, and enable
  it under the desktop's **Settings → AI Tool Limits**.
- **Ollama credits:** included credits, purchased balances and monthly credits
  used show their reported USD amounts. Legacy quotas still work; missing data
  stays unknown, and real zero or negative balances are preserved. API-key and
  Cookie setup stay on desktop.
- **Claude accounts:** multiple Web accounts appear alongside the automatically
  detected Claude Code account. Login renewal, enable switches and account
  removal remain desktop controls.

The twelve languages, payments, display controls, currencies, diagnostics and
optional live rates from v0.79.0 r2 are retained. Widget geometry, animations,
connection rules and dashboard processing frequency are unchanged. Notch Panel
changes stay on desktop, and Android remains read-only.

## Updating

Use **Settings → App updates**, or install the signed APK over the existing app
without uninstalling. Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

This release was not phone-tested. The nonblocking
[phone follow-up](https://github.com/The-Minion-oOo/token-monitor-android/issues/94)
remains open; emulator results do not establish phone battery or launcher behaviour.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
