# Changelog

## v0.83.0 r1 (candidate)

- Updated desktop compatibility to v0.83.0.
- Displayed Claude's reported Max/Ultra plan labels and verified weekly-only quotas, including zero and missing values.
- Verified Trae CN credit summaries, expired sessions and credential stripping through the released Node Hub and Worker.

- Added an off-by-default diagnostic build option for private Hub request timings.
- Overlapped the initial dashboard's independent reads, with at most two requests after verifying the Hub identity. All responses still finish before publishing fresh data.
- Kept the existing route protections, layouts, animations, update frequency and widget polling.

## v0.82.0 r1 — 2026-10-30

- Updated desktop compatibility to v0.82.0.
- Removed unsupported Codex cache-expiry estimates, including older cached readings.
- Retained cache-hit usage, speed and Claude tier estimates.
- Verified corrected archive totals and observed model breakdowns, including lower/zero corrections and missing evidence.
- Preserved the v0.81.0 r2 security fixes and existing display features.

## v0.81.0 r2 — 2026-10-29

- I hardened private Hub routing: HTTP requests and discovery bypass system proxies; Tailscale admission uses VPN link addresses, and private DNS/IPv6 checks fail closed.
- Bounded stream JSON nesting and retained reconnect backoff when a stream drops after its opening snapshot.
- Counted complete stream frames by received size, with incremental delta/freshness accounting.
- Skipped ineligible periodic widget jobs before pairing/cache work, recorded deadline failures, and completed successful reads before delayed cache persistence.
- Sent the app User-Agent on currency requests. Layouts, animations, update frequency and desktop compatibility stay unchanged.
- Checked the exact signed r2 APK on my phone: in-place upgrade retention, Pages redraw before opening, dashboard and Widget Live/Stop over Tailscale and home Wi-Fi, forced periodic refresh and offline recovery. Restored settings, removed the helper and turned debugging off. Comparable battery savings remain unmeasured; #94 stays open for broader feature checks.

## v0.81.0 r1 — 2026-10-29

- Updated desktop compatibility to v0.81.0.
- Added ordered v4 live updates with revision checks, freshness clocks and complete-snapshot recovery.
- Displayed Codex/Claude reasoning effort and personal Volcengine quota labels.
- Verified Codex price-tier labels and retained earlier Android features and languages.
- Included the merged Gradle, serialization and contract-tool dependency updates.

## v0.80.0 r1 — 2026-10-26

- I updated compatibility to desktop v0.80.0, including Muse Code quotas,
  Ollama credit balances and multiple Claude Web accounts.
- Kept Muse observations separate by device and retained Ollama plan labels.
  Included/purchased credits and monthly spend show their reported money values.
- Retained the twelve languages, payments, display controls, currencies,
  diagnostics and optional live rates from v0.79.0 r2.

## v0.79.0 r2 — 2026-10-26

- I added payment details and dated top-up ledgers, with separate recurring
  subscription intervals, renewal/end dates, account binding and notes.
- Added provider reset credits, grant/expiry explanations and token summaries.
- Added local tool hide/pin/order and provider/quota-window visibility controls.
- Added nine usage display currencies with cached automatic or manual rates;
  provider-native balances and recorded payments are preserved.
- Added preview, copy and share for a redacted diagnostic report.
- Added optional recent aggregate/per-model output speed and token burn,
  starting Off and using the existing dashboard update frequency.
- Added twelve app/widget languages, including Simplified/Traditional Chinese,
  Spanish, Hindi, Gujarati, Bengali, Tamil and Telugu, alongside English,
  Korean, Japanese and Brazilian Portuguese.
- Expanded emulator checks and documentation. Physical-phone follow-up is
  tracked separately in #94; this release was not phone-tested.

## v0.79.0 r1 — 2026-10-25

- I added the OmO Native label and cyan tool colour, separate from Pi and Oh My Pi.
- Checked desktop v0.79.0 OpenCode Console quotas and Zen balances, including
  unknown resets, missing windows, real zero and negative balances, and partial
  source failures. Android continues to read these values from the Hub.
- Retained optional HTTPS, background widget refresh and the r5 Tailscale
  Wi-Fi-only fix. Widget layouts and connection ownership are unchanged.

## v0.78.0 r5 — 2026-10-24

- I made **Wi-Fi only** for background widget refresh follow the network under
  Tailscale. Android can treat a VPN as metered even on home Wi-Fi, which could
  stop the job from ever running. The job now asks Android for any network, and
  each run checks the Wi-Fi underneath before and during its requests. This
  release was not phone-tested.

## v0.78.0 r4 — 2026-10-24

- I added optional background widget refresh at 30 or 60 minutes, starting Off
  with Wi-Fi only enabled. Runs yield to foreground activity and retain stale
  data on failure. An overnight preview phone check completed eight runs;
  I did not measure a matching battery baseline.

## v0.78.0 r3 — 2026-10-23

- I added confirmation before turning off internet access for a saved internet Hub.
  Cancel, Back and outside taps preserve the pairing. Private routes and initial
  pairing keep the plain toggle. This small UI change was not phone-tested.

## v0.78.0 r2 — 2026-10-23

- I added an off-by-default internet HTTPS Hub route, with Tailscale still recommended.
- I kept public HTTP blocked at saving, route and request boundaries, limited internet
  targets to public DNS names, and added clear TLS errors and a Hub-secret warning.
- I checked the pinned Cloudflare Worker locally alongside the Node Hub and added
  synthetic fixtures, consent migration tests and local HTTPS fixture tooling.
- I prepared the README, pairing, security, protocol and widget documentation.
- I verified HTTPS snapshots, freshness and changed stats on my phone over cellular.
- I checked the signed upgrade from r1: pairing survived and Tailscale/home Wi-Fi connected.

Android releases use the desktop protocol version plus an Android revision.
Unreleased work stays under **Next release** until its signed APK is published.

## v0.78.0 r1 — 2026-10-23

- I checked Android against desktop v0.78.0 and added released-Hub coverage for
  ZCode titles, subagent/workflow grouping and separate fork/side-chat rows.
- I verified that titles stay private by default, follow desktop consent and
  disappear on revocation. Android's local title preference still hides them.
- I retained the v0.77.0 r2 performance fixes, five-second complete-update limit,
  widget layouts, animations and existing read-only network behavior.

## v0.77.0 r2 — 2026-10-21

- While a coding session is active the Hub sends a complete update every few
  seconds. I limited the dashboard to at most one complete update every five
  seconds, always ending on the newest. This reduces decoding and redraw work
  on the phone; the Hub still sends every update.
- Desktop compatibility stays at v0.77.0. After release I checked the in-app
  update from r1 on my phone; the Pages widget redrew without opening the app.

## v0.77.0 r1 — 2026-10-21

- I updated the desktop baseline to v0.77.0 and added New API account spend
  for Today, Month, the rolling seven days and All time when reported.
- I checked separate Cline account identities and missing Cursor quotas against
  the released Hub. Missing spend stays unknown; a reported zero stays zero.
- I reduced repeated JSON conversion, moved session/history preparation and
  cache hydration off the UI thread, and reused unchanged update data.
- Dashboard instances now hold separate Live ownership, and Refresh restores
  the visible dashboard's connection without letting Settings start a stream.
- Widgets share snapshot reads and avoid duplicate Live redraws. The responsive
  chart uses its measured image size while keeping the existing layout.
- Dashboard update frequency, animations and network permissions are unchanged.
  This candidate is not phone-tested; the Pages redraw check after an in-place
  install is still pending.

## v0.76.0 r1 — 2026-10-20

- I updated the desktop baseline to v0.76.0, including the changes in v0.74.0
  and v0.75.0, and merged the Compose compiler 2.4.21 patch.
- I added separate 1–50 Home list counts for Tools, Models, Sessions and
  Devices, preserving saved layouts and the existing Android defaults.
- Expanded projects can switch between Tools and Models. Missing model
  attribution stays Unknown model rather than an estimated split.
- Antigravity subagents group under their conversation. Grok Bot records need
  an explicit open turn before recent usage can appear Running.
- I checked Argon quotas, missing versus exhausted readings, Grok reasoning
  totals and classified history zeros against the released desktop code.
- I kept the widget layout, digit roll, network rules and updater unchanged.

## v0.73.0 r1 — 2026-10-16

- I updated the desktop baseline to v0.73.0 and checked Cursor's current-period
  quotas, matched timing counters and corrected zero usage against the released Hub.
- I hide Oh My Pi's unsupported leading title icon while keeping its title code,
  text and stored record intact.
- Claude Desktop usage remains under Claude, and Windows Goose usage keeps its
  Goose label. Collection and transcript details stay on desktop.
- I kept the widget design, network rules, digit roll and on-demand updater unchanged.

## v0.72.0 r1 — 2026-10-16

- I updated the desktop baseline to v0.72.0 and checked Cursor's included
  weekly/monthly usage and optional prepaid balance against the released Hub.
- I calculate grouped-session cache hit and output speed from summed counters,
  show the combined message count, and use the latest member's activity time.
  Continuations keep their subagent label when their parent is outside the list.
- I kept widget layouts, digit roll, network route rules and on-demand Android
  updates unchanged. Desktop update and transcript changes stay on desktop.

## v0.71.0 r1 — 2026-10-14

- I updated the desktop baseline to v0.71.0 and added fixtures and released-Hub
  checks for Goose, Notion AI allowances and separate WorkBuddy accounts.
- I added the Goose and Notion marks and desktop labels. Notion's Rolling and
  Monthly windows use the existing quota display without merging workspaces.
- I kept digit roll, session grouping, route rules and widget layouts unchanged.

## v0.70.0 r1 — 2026-10-14

- Follow desktop v0.70.0 with pinned-source contract checks and synthetic
  endpoint/stream fixtures, retaining earlier compatibility tests.
- Roll token totals the way the desktop now does: each changed digit rolls in
  while unchanged leading digits stay put, on the Home total and in breakdown
  rows. Settings → Appearance has a new Token number animation choice, Digit
  roll (the default) or Count up (the previous behavior). Reduce motion still
  turns animation off. The roll keeps the desktop's timing, scale and fade but
  not its brief blur.
- Show CatPaw sessions and usage under the desktop's CatPaw name.
- Show a MiniMax pay-as-you-go key's CNY or USD balance in Limits with the
  desktop's full balance bar, and name the MiniMax plan (Token Plan or
  Pay-as-you-go).
- Print balances and spend with a `¥` or `$` symbol and two decimals, as the
  desktop does, so a MiMo wallet now reads `¥42.00` instead of `CNY 42`.
- Skip session grouping work on large session lists when no session links to
  another, matching the desktop's faster session lists.
- Keep read-only access, widgets and the release signing identity unchanged.

## v0.69.0 r1 — 2026-10-12

- Follow desktop v0.69.0 with pinned-source contract checks and synthetic
  endpoint/stream fixtures, retaining earlier compatibility tests.
- Group Codex subagents and continuation files, and T3 Code continuations and
  delegated runs, under their main session with combined usage.
- Group Cursor Grok Bot activity records, and gather Codex background reviews
  as Codex Auto Review.
- Show "Waiting for you" in Home and Sessions for Claude Code and Codex
  sessions that are waiting on a reply or approval.
- Keep read-only access, widgets and the release signing identity unchanged.

## v0.68.0 r2 — 2026-10-11

- Keep the Hub secret on the network its address belongs to: Tailscale only
  while a VPN is up, the home address only on Wi-Fi, Ethernet or a VPN, never
  directly over mobile data. Private names must resolve to private addresses,
  and Find searches only Wi-Fi.
- Line the Pages widget's touch targets up with the drawn card at every size
  and orientation, show every status word in full, draw the card off the main
  thread and stop refused widget updates from crashing the app.
- End Live when the Hub rejects the secret, start a fresh hour after an expiry
  during sleep, and satisfy Android's foreground-start rule in every path.
- File live totals under the desktop's day across time zones, skip oversized
  optional endpoints instead of failing, reuse decoded history between live
  updates and ignore non-finite numbers.
- Keep the selected period across rotation, darken light-theme warning text,
  round quota percentages the same way everywhere, roll compact totals over
  cleanly and fix a crash on duplicate limit rows.

## v0.68.0 r1 — 2026-10-08

- Follow desktop v0.68.0 with pinned-source contract checks and synthetic
  endpoint/stream fixtures, retaining earlier compatibility tests.
- Show reported unpriced tokens alongside known costs across totals, breakdowns,
  projects, sessions and history. Widget cost slots use a compact uncertainty
  marker with the count in their accessibility descriptions.
- Label Codex Dots sessions as observed-only rather than complete history.
- Keep collection on desktop, with no new permissions, background work or
  changes to widget coordinates and type sizes.

## v0.67.0 r1 — 2026-10-06

- Follow desktop v0.67.0 with pinned-source contract checks and synthetic
  endpoint/stream fixtures, retaining older compatibility coverage.
- Accept TM2 chart-color codes while preserving TM1, preset appearance and
  vendor colors. Custom chart colors reach Trends and both widget providers.
- Separate MiMo Console and Desktop Membership rows, keep wallet balances in
  native currency, and distinguish tracked daily/weekly spend from provider totals.
- Verify default title stripping, opt-in titles, revocation and transcript
  exclusion against the released Hub. Android remains read-only.

The r2 Wi-Fi recovery, bounded widget sessions, fixed widget geometry and
existing signing identity are retained.

## v0.66.0 r2 — 2026-10-04

- Request Android 17 local-network access when connecting or searching over
  home Wi-Fi, with recovery after denial and Tailscale available independently.
- Test and save a changed home address using the stored pairing secret. A
  discovered address is not saved until that check succeeds.
- Enforce the widget Live deadline across device sleep and between requests;
  show reconnecting and saved-data age in its notification.
- Preserve unattributed usage and missing calendar days in Trends. Search
  visible session titles, identifiers and tool names, with clearer empty states.
- Add selected-control and chart semantics, page-specific widget summaries,
  and consistent quota urgency across Home and Limits.
- Avoid decoding the complete stats response twice on live updates while
  retaining previously received device history.
- Add a contract check against pinned released Hub source, synthetic performance
  baselines, Android 17 coverage, and exact-commit release checks.

The desktop baseline stays v0.66.0. Widget geometry and the existing release
signing identity are retained.

## v0.66.0 r1 — 2026-10-04

- Follow desktop v0.66.0 with synthetic fixtures for every Hub read endpoint,
  complete/freshness stream events and retained earlier compatibility tests.
- Recognize MiniMax Code as `mcode`, using its display label and MiniMax mark
  across the existing app and widget surfaces, separate from MiniMax limits.
- Preserve desktop-supplied costs in current usage and history, including
  custom-pricing corrections; no Android-side pricing or collection is added.
- Keep the widget geometry, private read-only connection, bounded Live behavior
  and release signing identity unchanged.

## v0.65.0 r1 — 2026-10-02

- Follow desktop v0.65.0 with versioned fixtures for every Hub read endpoint and
  complete/freshness stream events; retain earlier protocol regressions.
- Add fx's lowercase label and upstream mark.
- Show session-average generation speed, cache-hit percentage and optional
  prompt-cache estimates in Home and Sessions when reported.
- Add a local **Show session titles** setting. Hiding titles changes display
  only; the snapshot cache may still contain them.
- Keep the Pages widget layout, private read-only connection, release signing
  identity and bounded Live behavior unchanged.

## v0.64.0 r1 — 2026-09-30

- Match the released desktop v0.64.0 Hub with new synthetic endpoint and stream
  fixtures, retaining earlier compatibility tests.
- Add Muse Code's label and mark and read StepFun's optional plan name alongside
  its Coding and Token Plan windows.
- Add recent/running sessions to Home, with reported titles and recent context.
  New installs show Sessions by default; existing saved Home layouts stay intact.
- Keep archived/deleted sessions idle. Preserve Grok Build titles, Codex plan
  labels, and OpenRouter's normalized resetting allowance.
- Keep session rows distinct when different tools report the same session ID.
- Leave the Pages widget geometry, signing identity, private read-only Hub access,
  and bounded Live behavior unchanged.

## v0.63.1 r1 — 2026-09-28

- Verify the desktop v0.63.1 Hub contract with sanitized fixtures for all five
  read endpoints, a complete stream event, and a freshness update.
- Preserve Cursor's canonical `cursor-auto` model across totals, sessions, and
  history, plus the reasoning-inclusive ZCode and OpenCode counters reported by
  the desktop. No Android-side usage collection or widget layout change.
- Keep daily output and cache totals when a matching sparse history preview
  follows the full Hub history response; changed days cannot reuse stale totals.

## v0.63.0 r1 — 2026-09-27

- Verify the desktop v0.63.0 Hub read contract with sanitized examples for all
  five endpoints, complete stream delivery, and freshness-only updates.
- Show a reported Cursor conversation title in Sessions while retaining the
  client/model fallback for older or untitled sessions. No transcript bodies are
  requested or synchronized by the Android app.
- Keep the four-page widget layout and background-work behavior unchanged.

## v0.62.0 r1 — 2026-09-24

- Add an on-demand update check in Settings for published Android releases.
- Verify a downloaded APK's size, SHA-256, package, version, and signing
  certificate before handing it to Android's installer for user confirmation.
- Include a machine-readable update manifest in future release assets.
- Verify the v0.62.0 Hub contract with a new sanitized fixture for all read
  endpoints and stream events. Keep Pi and Oh My Pi usage distinct, show
  TypeSafe's plan and balance, and retain Devin's reported plan.

## v0.61.0 r1 — 2026-09-23

- Verify every Android read endpoint against desktop Token Monitor v0.61.0 and
  add a sanitized full-endpoint fixture while retaining the v0.54.0 through
  v0.60.0 compatibility cases.
- Recognize the canonical Xiaomi MiMo and Devin clients, the Cline and Devin
  limit providers, GitHub Copilot, and the desktop's current provider names.
- Add the upstream Cline and Devin marks and keep older MiMo client IDs readable
  after the desktop's normalization to `mimo`.
- Preserve the r7 Pages widget geometry and type scale; this compatibility
  release changes data identity and presentation only.

## v0.60.0 r7 — 2026-09-22

- Give both dense Breakdown columns one two-line row with room between the
  lines: name and share, then tokens and cost, then the bar. Rows line up
  across the divider and nothing touches its neighbour.
- Center the app icon, the Limits provider marks and the Breakdown vendor
  marks on the text lines beside them instead of on the first line alone.
- Show today's cost in the Activity stat slot when the Hub reports no message
  count for the day, instead of a false "0 messages".
- Give the common one-tool/one-model Breakdown state a dedicated feature
  layout with large, aligned token and share figures, explicit captions, and
  full-width bars. Dense multi-row data keeps the compact table layout.

- Verify the desktop Token Monitor v0.60.0 Hub contract while retaining the
  v0.54.0, v0.55.0, and v0.56.0 compatibility fixtures.
- Negotiate stream protocol v2 and merge lightweight freshness events into the
  last complete snapshot without dropping periods, limits, devices, or history.
- Show a session as Running, Finished, or Idle from the new tri-state turn signal,
  and show recent context-window use when both token values are available.
- Recognize the current Factory credit/allowance limit shapes and add Qwen,
  NVIDIA, and StepFun provider presentation without changing the read-only boundary.
- Rebuild the four widget pages on one measured grid written down in
  `docs/WIDGET_SPEC.md`. Every position and type size now comes from that
  specification, the card scales as one picture to any launcher size, and the
  Overview stats, Limits cells, Breakdown rows and Activity charts match the
  approved concept cards instead of colliding.
- Bundle a Latin subset of JetBrains Mono for the widget pages so Samsung
  launchers, which substitute their own monospace face, render the same card
  the emulator does. Figures keep the system sans-serif with tabular numerals.
- Group the Limits page by provider, each with its two tightest windows, and
  draw bars, the seven-day chart and the heatmap as vectors on the card instead
  of scaled bitmaps.
- Render the card at the launcher's display density instead of enlarging a
  560-pixel raster. Type, icons, borders, and charts stay sharp on high-density
  Samsung launchers while remaining inside Android's widget bitmap budget.
- Fix eleven imported vendor logos whose SVG arc flags Android's path parser
  rejects. Drawing a Qwen, Gemini, Meta, Cohere, Kimi, MiniMax, Doubao, Hunyuan,
  OpenRouter, xAI or Xiaomi mark in the widget crashed the render.
- Render the widget gallery and the widget design test from the dense showcase
  fixture rather than a two-row protocol sample, so emulator evidence shows the
  card at real density.
- Split stream delivery and session display rules into focused modules so future
  desktop upgrades remain fixture-and-adapter work instead of screen rewrites.

## v0.56.0 — September 13, 2026

### r4

- Verify protocol compatibility with desktop Token Monitor v0.56.0, including
  typed reset/expiry boundaries and background-review session metadata. Older
  v0.54.0 and v0.55.0 Hub responses remain supported.
- Add a separate medium/large pages widget with Overview, Limits, Breakdown,
  and Activity backed by one cached snapshot. The launcher receives one fixed
  1.82:1 card instead of a collection, preventing One UI from shrinking and
  fanning the pages.
- Add accessible left and right edge controls plus four page dots. Changing a
  page is local and adds no timer, network request, or background work.
- Use one shared typography scale across all four pages. Only the headline token
  total is intentionally larger; long provider and model names truncate instead
  of forcing smaller text.
- Replace the generic deck picker art with a fixed, populated preview that uses
  the same wide proportions and information hierarchy as the installed widget.
- Replace the widget refresh text glyph with a vector icon in a transparent
  48 dp touch target while retaining the existing five responsive widget sizes.
- Keep immediate streaming while the dashboard is open, and use a lighter
  30-second stats refresh during an explicit widget Live session.
- Repost the Live notification only when its visible content changes.
- Finish queued snapshot saves and cache clears before repository shutdown.

## v0.54.0 — September 8, 2026

### r20

- Use the overview widget layout at Samsung's common 4×2 height, with responsive
  tool and limit detail as space permits.

### r19

- Show current usage and the tightest provider windows in the Live notification.
- Request Android 16 notification promotion and declare lock-screen widget
  eligibility where the launcher supports it.

### r18

- Replace the widget button row with compact Refresh and Live controls.
- Add responsive message, active-time, streak, tool-share, quota-window, and
  seven-day details using data already present in the saved snapshot.

### r17

- Label the active Tailscale, home Wi-Fi, or private-network route.
- Add a setting that follows the phone's light and dark mode.
- Distinguish same-named limit windows by their period on Home, Limits, and widgets.

### r14

- Reduce the visual weight of widget controls while preserving 48 dp touch targets.
- Correct README hero and gallery alignment.

### r13

- Redesign all widget sizes with dashboard typography, sharp size-aware charts,
  responsive spacing, full counters, Refresh, and bounded one-hour Live mode.
- Add tool-filtered model pages, heatmap day details, usage search, completed-period
  comparisons, and denser dashboard lists.
- Improve stream cancellation, snapshot recovery, retained device history, and
  shared repository ownership between the dashboard and widget service.
- Split the Compose interface into focused screen and shared-component files.

## v0.54.0 — September 7, 2026

### r5

- Open activity-heatmap days to show their date, tokens, and cost.

### r4

- Add interface themes, desktop theme codes, live indicators, and tool-icon
  preferences.

### r3

- Make Settings sections expand one at a time.
