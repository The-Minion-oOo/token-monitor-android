# Android v0.88.0 r1

I updated the Android companion for desktop Token Monitor v0.88.0.

## What's new

- **New tools.** See Mistral Vibe, Prime Agent and Jcode usage with the desktop names.
- **Mistral limits.** Check API and Vibe allowances, reset times and credit balances.
- **MiniMax products.** Code points and plan quotas have a separate heading from
  API balances and spend. Multiple API accounts keep their own readings.
- **Desktop model names.** Models, Sessions and Pages Breakdown follow the aliases
  and grouping saved on desktop.
- **Calmer reconnects.** Brief interruptions stay quiet for three seconds. Longer
  failures show one notice with the relevant connection error.
- **Useful Live status.** The notification leads with running or waiting
  conversations, then a critical quota, then today's usage, with Stop available.

## Updating

Use **Settings → App updates**, or install the signed APK over the current copy.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I checked the released Hub and Worker, automated tests and emulators. I also
installed the signed APK over v0.87.0 r1 on my phone. Saved pairing and settings
survived. Fresh data worked over Tailscale and the Home Wi-Fi fallback, all four
Pages views rendered, and Live showed the running count and stopped correctly.

Android marked the Live notification promoted, but the status-bar chip did not
appear. I am keeping
[#146](https://github.com/The-Minion-oOo/token-monitor-android/issues/146)
open for that behaviour and the remaining phone checks. Battery use and
automatic one-hour expiry were not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
