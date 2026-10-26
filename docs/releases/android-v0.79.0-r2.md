# Android v0.79.0 r2

This is a release candidate for desktop Token Monitor v0.79.0. The published
download remains r1 until the signed r2 APK is released.

I added the useful read-only dashboard features that were still missing from
the Android companion:

- Payment details, recurring intervals, renewal/coverage dates and dated top-up
  ledgers. Top-ups no longer look like zero-cost monthly subscriptions.
- Reset credits, expiry and grant explanations, plus reported provider token
  summaries.
- Local tool hide/pin/order and provider/quota-window visibility controls.
- Nine usage display currencies with saved automatic or manual exchange rates.
  Provider balances and recorded payments keep their original currency.
- Optional recent output speed and token burn, including reported model rates.
  It starts Off, expires idle samples and uses the existing dashboard updates.
- A diagnostic report that can be previewed before copying or sharing, with
  addresses, secrets, identities and conversation text excluded.
- Twelve interface languages: English, Simplified/Traditional Chinese, Korean,
  Japanese, Brazilian Portuguese, Spanish, Hindi, Gujarati, Bengali, Tamil and
  Telugu. Widgets use the chosen language too.

The dashboard processing limit, Kotlin/Compose layout, animations, read-only
Hub boundary and widget geometry are preserved. Currency requests are optional
and foreground-only; there is no runtime translation service.

This candidate was not tested on a physical phone. The follow-up is
[#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94), separately
from the automated and emulator release gates. See
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md)
for the results and their limits.

[Installation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md) ·
[Languages](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/LANGUAGES.md) ·
[Desktop coverage](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/DESKTOP_PARITY.md)
