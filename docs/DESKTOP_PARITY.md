# Desktop parity for v0.80.0

I compare the Android companion with released desktop source, rather than
unreleased desktop changes. The baseline is tag `v0.80.0`, commit
`c61cbe998d69725589c03511f2f56d43714f4c0f`. Android remains a read-only
dashboard: the desktop collects usage, signs in to providers and hosts the Hub.

The latest published APK is **v0.80.0 r1**.
Items marked **r2** below were introduced in v0.79.0 r2 and are retained.

Desktop v0.80.0 adds Muse Code session/weekly quotas, keeps its device observations
separate, and displays Ollama's included/purchased credits and monthly spend in
their native currency. Claude Web accounts appear alongside automatic Claude
Code readings. Login renewal, account enable/removal and Ollama credentials
remain desktop settings; Android reads the resulting Hub rows. Notch Panel
spacing, hover and session-summary changes remain desktop-only.

| Desktop feature | Android coverage |
| --- | --- |
| Totals and periods | Day, month, week, rolling 7/30 days and total; configurable default |
| Home | Reorder or hide modules; separate 1–50 item counts for Tools, Models, Sessions and Devices |
| Tools and models | Tokens, estimated cost, ranking, cache/input/output detail; tap a tool to filter models |
| Local item controls | **r2:** hide, pin and reorder tools; hide providers, individual quota windows, provider details and spend |
| Limits | Provider windows, remaining/used bars, reset/expiry countdowns, plan, source and optional account email; native-currency balances and spend |
| Provider details | **r2:** reset-credit counts, expiry and grant eligibility; reported TypeSafe period/today/week tokens, input/output, requests and estimated spend |
| Recorded payments | **r2:** recurring intervals and renewal/end dates, account binding, notes and dated top-up ledgers. Top-ups are not monthly subscriptions |
| Display currency | **r2:** USD, CNY, TWD, HKD, INR, EUR, JPY, KRW and BRL; saved automatic or manual rates. Provider balances and payments retain their reported currency |
| Live speed and burn | **r2:** optional recent aggregate/per-model output tokens/s or tokens/min from matched cumulative counters. Off by default; separate from existing session-average speed |
| Languages | **r2:** English, Simplified/Traditional Chinese, Korean, Japanese, Brazilian Portuguese, Spanish, Hindi, Gujarati, Bengali, Tamil and Telugu; follows the phone or an explicit app choice |
| Diagnostics | **r2:** preview, copy or share an allowlisted report with versions and state flags; no addresses, secrets, account identities, conversation text or cached responses |
| Devices | Reported totals, clients, models, collection cadence, last upload and retained history |
| Projects and sessions | Search, expandable tool/model shares, reported titles and session relationships. Codex/T3 continuations and subagents are grouped; forks and side chats stay separate |
| Session context | Reported Running/Waiting/Idle state, context-window use, cache-hit percentage and optional prompt-cache estimates |
| History | Overview cards, daily heatmap, period comparisons, model/tool exploration, bars and K-line ranges; missing days stay missing |
| Service status | Claude, OpenAI, Cursor and DeepSeek status, incidents and status-page links |
| Appearance | Desktop TM1/TM2 themes, independent chart colour, light/dark following, tool marks, compact totals, text size, reduced motion and number animations |
| Widgets | Responsive Usage widget and fixed four-page Pages widget; saved snapshots, one-shot Refresh, bounded one-hour Live and optional 30/60-minute background refresh |
| Connections | Recommended Tailscale, optional home Wi-Fi/failover and separate opt-in public DNS HTTPS with system-CA TLS |

Local visibility controls change presentation, never collection or the Hub's
accounting totals. Live-rate attribution depends on what the Hub sends; large
desktop sync payloads can omit per-model throughput. Android re-baselines when
sources or capabilities change and expires idle samples after eight seconds.
It does not infer a model rate from a lifetime average or request updates faster.

## Intentional desktop responsibilities

Provider sign-in, account management, collector settings, pricing/alias edits,
subscription editing, Hub hosting, device deletion and shared-setting writes
remain on desktop. Android does not read transcript bodies, local credential
files, collector logs or absolute project paths. Its diagnostic report describes
the companion, not the desktop's files.

Tray, notch, Edge Dock, floating bubbles, macOS haptics, global shortcuts,
Discord presence and desktop startup/window effects have no direct Android
equivalent. The phone uses native screens and launcher widgets.

## Evidence

Versioned fixtures and the pinned Node/Worker Hub contract cover the external
protocol. Emulator checks cover UI behaviour and local settings. Physical-phone
results apply only to the build named in [Validation](VALIDATION.md).
The r2 phone follow-up is [#94](https://github.com/The-Minion-oOo/token-monitor-android/issues/94).

See [Architecture](ARCHITECTURE.md), [Protocol](PROTOCOL.md),
[Languages](LANGUAGES.md) and [Upstream sync](UPSTREAM_SYNC.md).
