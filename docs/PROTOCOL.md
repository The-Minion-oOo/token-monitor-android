# Hub protocol compatibility

The v0.79.0 r2 release additionally retains optional `resetCredits` (including
expiry/grants), `usageSummary`, period `modelThroughput`, and subscription
`kind`, `intervalCount`, `binding`, `topUps`, renewal/end dates and notes. Missing
optional blocks keep older Hubs compatible. Top-up payments and recurring
amounts are separate, and all Hub requests remain read-only. The released-source
contract checks these normalized fields alongside the existing endpoints and
stream frames.

This Android app treats the desktop Hub as an external, read-only protocol.

## Verified baseline

The verified baseline is upstream Token Monitor **v0.80.0**, released 2026-10-26.
I check each release against the `Javis603/token-monitor` tag recorded in
`upstream.json`; that tag, not a local checkout, is the protocol reference.

Desktop v0.80.0 adds Muse Code session/weekly quotas and device-scoped observations,
Ollama API/Cookie credits and multiple Claude Web accounts alongside automatic
Claude Code readings. The parser keeps optional window `used` amounts for
reported spend and Ollama's legacy plan label. Included credit meters retain
their reported ratio; purchased balances and spend with `showMeter: false`
do not acquire an invented percentage. Zero and negative purchased balances
remain distinct from missing data. Credentials and account management stay on desktop.

The v0.79.0 fixtures include OmO Native (`senpi`) independently of Pi and Oh My
Pi. Reasoning is already included in its output count. OpenCode Console fixtures
come from the released collector through the Hub and retain Go quota windows
separately from the Zen USD balance. Missing windows stay absent, missing reset
times stay unknown, and a real zero or negative balance stays numeric. A failed
Go or Zen request does not discard the healthy source. Android reads the Hub;
it never receives the Console cookie or contacts the Console itself.

The current Node Hub binds to loopback when no secret is configured. A secret is
therefore required before the Hub can bind to a Tailscale or LAN address. The
Android app always sends the secret as `Authorization: Bearer <secret>` and
never prints it.

| Read endpoint | Authentication | Android use |
| --- | --- | --- |
| `GET /api/health` | No | Verify that the target identifies itself as a Hub. |
| `GET /api/stats` | Bearer secret | Main snapshot: periods, limits, devices, projects, sessions, and history preview. |
| `GET /api/devices` | Bearer secret | Device periods, platform metadata, tracked tools, cadence, and retained per-device history. |
| `GET /api/history` | Bearer secret | Daily/monthly totals and per-tool/per-model trend attribution. |
| `GET /api/subscriptions` | Bearer secret | Subscription dashboard. |
| `GET /api/stats/stream` | Bearer secret | SSE `snapshot`, `stats`, and v2 `freshness` frames while a dashboard is visible. |

The Hub also accepts `X-Token-Monitor-Secret`; this app intentionally uses one
authentication style only. It does not call the Hub's ingest, subscription
write, or device deletion endpoints.

## Cloudflare Worker

I also exercise `worker/src/index.js` from the same pinned v0.78.0 commit locally.
Its five read routes use the same Bearer authentication and unauthenticated Hub
identity check. Stream-v2 negotiates the same `snapshot`, complete `stats` and
compact `freshness` envelopes. Ingest changes may produce freshness-only events;
forced broadcasts such as subscription changes send complete stats. Heartbeats
are SSE comments. No Android protocol change is needed for those routes.

The Worker runs independently of the desktop process and reports a different
runtime/build identity. Its subscriptions response includes `ok`; Android ignores
that extra field. A Worker without a configured secret returns `503 secret_required`
for protected routes. The existing unavailable path handles it.

`check-hub-contract.mjs` exports the pinned Worker along with the Node Hub into
the disposable contract directory. Its sibling `check-worker-contract.mjs`
uses the actual Worker handler with in-memory synthetic storage and Request
objects; it does not deploy or contact any Hub. It verifies missing/wrong auth,
read responses, all three stream events, and conversation-content stripping.
Android compatibility tests parse those generated responses and retain sanitized
Worker fixtures alongside the Node fixtures.

## Errors and streaming

The verified Hub returns `{ "error": "unauthorized" }` with HTTP 401 for a
protected read without a valid secret, and `{ "error": "not_found" }` with
HTTP 404 for an unknown route. The app gives a specific safe message for those
conditions and does not surface request headers or the secret.

The v0.77.0 Node Hub can return HTTP 503 with `hub_stopping` while shutting down.
Android's existing unavailable/retry path handles that response and closed
connections; it does not change polling or stream processing frequency.

Android sends `x-token-monitor-stream: 2` when it opens `/api/stats/stream`.
The Hub sends an initial `snapshot`, complete `stats` events when values change,
and small `freshness` events for limit and device timestamps. Each data envelope
contains `stats`. The Hub also emits an SSE comment heartbeat. The app closes the underlying stream in
`onPause`, opens it only on a dashboard route, refreshes first on resume, and
retries a disconnected stream with 1–30 second bounded exponential backoff plus
jitter.

`HubStreamProtocol` treats complete and incremental delivery separately from the
stable snapshot parser. A freshness frame may update only the top-level update
time, stale threshold, limit freshness, and device freshness fields. It is merged
into the last complete wire snapshot by device ID, preserving periods, sessions,
limits, provider details, and retained device history. A foreground refresh still
replaces the snapshot with the current read endpoints.

## Fixtures

For v0.78.0, ZCode titles and parent links use the existing optional session
fields. Tool- and workflow-spawned children fold under their parent; deliberate
forks and side chats stay separate. Titles are absent unless desktop sharing is
enabled and disappear again when consent is revoked. Android's local title
preference can hide a shared title without changing desktop consent.

The v0.78.0 fixtures cover all five read endpoints, complete/freshness stream
frames and ZCode with sharing off, on and revoked. The contract harness creates
a synthetic ZCode database in its disposable home, runs the released metadata
resolver and passes the result through the actual Hub. Android never reads that
database or calls a title-policy write endpoint. Prompt and message sentinels
must stay out of every response.

For v0.77.0, `thirdparty` accounts with `adapterId: "newapi-account"` can carry
`balance.todaySpend`, `weekSpend`, `monthSpend` and `allTimeSpend` with a native
currency. Android shows only finite, nonnegative reported amounts, including
zero. Week is a rolling seven-day period. These provider billing totals do not
use MiMo's locally tracked labels. Other adapters retain their prior mapping.

Synthetic v0.77.0 responses from the pinned Hub cover all five read endpoints,
the live stream and provider billing in `app/src/test/resources/protocol/v0.77.0/`;
the retained v0.76.0 and v0.54.0, v0.55.0,
v0.56.0, v0.60.0, v0.61.0, v0.62.0, v0.63.0, v0.63.1, v0.64.0, v0.65.0, v0.66.0, v0.67.0, v0.68.0, v0.69.0, v0.70.0, v0.71.0, v0.72.0 and v0.73.0 fixtures prove backward compatibility. They contain no user secrets,
machine paths, account identifiers, or real usage. Parser tests cover every
read surface, the SSE envelope, omitted optional fields, and a future unknown
field. The compatibility parser maps only the fields the dashboard needs and
ignores the rest. v0.54.0 coverage includes project `tokens`, session counts
and times, device OS/cadence/history, limit labels and balances, and history
component provenance inside `perClient` and `perModel`.

v0.55.0 adds `capabilities.throughput` and the optional period fields
`timedTokens`, `timedOutputTokens`, and `timedDurationMs`. Android maps and tests
those values, treats absent or explicitly unavailable throughput as unknown,
and does not display a live period or per-model rate. v0.65.0 adds a separate
session-average generation speed when that session reports timed output.

v0.56.0 adds optional `windows[].boundaryKind` lifecycle wording and
`periods.*.sessions[].sessionKind` metadata. Android preserves both, renders
reset, expiry, and mixed boundaries accurately, and keeps older omitted fields
on their legacy behavior.

v0.60.0 adds optional session `contextTokens`, `contextWindow`, and tri-state
`turnEnded` fields. `false` means a recent turn is still running, `true` means
the turn ended, and an omitted value remains unknown; the parser does not collapse
those states. Android shows context use only when both positive values are present
on a recent Running or Finished session. The same fixture covers current Factory
percentage and credit allowance shapes plus stream-v2 complete and freshness events.

v0.61.0 keeps the read endpoint and session shapes stable. It normalizes Xiaomi
MiMo usage to the canonical `mimo` client, adds `devin` usage, and adds Cline and
Devin limit providers. Android recognizes those identities, keeps the legacy
`micode` label readable, and retains the existing Copilot and context-window
fields. Desktop-only Edge Dock, floating bubble, and macOS haptic changes do not
cross the Hub contract.

v0.62.0 keeps the five read endpoints and stream envelope. The Node Hub now
requires the secret in a header, which Android already sends as Bearer auth.
Usage identifies Oh My Pi as `omp` separately from `pi`. TypeSafe supplies a
credit-balance window and its plan in `accountLabel`; Devin may supply
`planLabel`. Android renders those available values and safely ignores optional
Claude reset-grant and provider usage-summary details it does not yet display.

v0.63.0 retains those endpoints, header authentication, and stream-v2 envelope.
The release changes mostly desktop presentation, collection, and limits-account
setup. Cursor may now send a locally resolved conversation `title` on a session;
Android reads that optional field and keeps its client/model fallback when absent.
The title is metadata visible in Home and Sessions, not a prompt or response body. The
new Antigravity and Qoder CN source checks affect desktop collection, not phone
access to the Hub. The v0.63.0 fixture is synthetic and follows the released
wire contract; it is not an unredacted Hub capture.

v0.63.1 leaves `docs/API.md`, the Hub routes, header authentication, and the
stream-v2 envelope unchanged. Desktop normalization now reports Cursor Auto as
`cursor-auto` across current usage and historical model buckets, while ZCode and
OpenCode usage includes disjoint reasoning tokens in the reported output totals.
Android preserves those server-reported IDs and counters without recomputing
them. The v0.63.1 fixture is synthetic and covers that data shape, not a real
Hub capture. Collection-worker and Electron renderer performance changes stay
on the desktop.

v0.64.0 keeps the five read routes, Bearer authentication, and stream-v2
contract unchanged. Muse Code uses client ID `muse`; its reported output already
includes disjoint reasoning tokens. Grok Build supplies optional title/project
metadata through the existing session shape. StepFun's optional plan name is in
`accountLabel`, used only for StepFun and TypeSafe when `plan`/`planLabel` are
absent. Coding Plan reports five-hour and weekly windows; Token Plan reports a
credit percentage whose reset may be absent. Codex's normalized plan labels and
OpenRouter's normalized resetting key allowance pass through unchanged.

Optional session `archived`, `deleted`, and `sourceDeleted` flags map to one
archived state so a recently closed session cannot show Running or context use.
Omitted flags remain false. Home derives its recent list from month/today and
does not invent sessions for rolling history ranges. The v0.64.0 fixture is
synthetic, covers every read endpoint and both stream event forms, and contains
no transcript or real account data.

When the desktop protocol changes, add a new versioned fixture directory and
tests before changing the Android mapping.

## v0.65.0 session metrics

The read routes, Bearer authentication and stream-v2 envelope are unchanged.
Sessions may provide nonnegative `inputTokens`, `outputTokens`,
`cacheReadTokens`, `cacheWriteTokens`, `timedOutputTokens` and
`timedDurationMs`. Android caps timed output at reported output and treats
missing/zero duration as unknown, not zero speed. The displayed tok/s is the
session average, not a live or per-model sampling rate. Cache hit is the share
of input served from cache: reads / (input + reads + writes). No cache traffic
means no reading; cache writes without reads can legitimately show 0%.

Optional `promptCache` contains `observedAt` and a `ttlSeconds` of 300, 1800,
or 3600. Missing, null or invalid estimates are ignored. Codex and Claude rows
show a rounded-up minute estimate only until expiry; archived and future-dated
observations are suppressed. This is not a guarantee of provider-side retention.
The existing slow UI clock updates the label without another timer or request.

The optional period `modelThroughput` map is tolerated and retained in the raw
cached snapshot, but Android does not display per-model throughput. Desktop
provider usage-row visibility is a desktop setting, not a new Hub write API.

## v0.66.0 MiniMax Code and reported costs

The Hub routes, Bearer authentication and stream-v2 envelope are unchanged.
MiniMax Code usage uses client ID `mcode`, distinct from the `minimax` limit
provider and MiniMax model IDs. Android supplies its label and existing vendor
mark without renaming the wire keys. The new synthetic fixtures cover all read
endpoints, complete/freshness events, MiniMax's normalized five-hour and weekly
windows, title-free sessions and corrected current/historical costs. They are
contract examples checked against the released source, not real account captures.

Desktop custom pricing, including one-hour cache-write rates, changes the costs
the Hub reports. Android displays those values without recalculating prices or
adding another collector. MiniMax API-region selection, collection and device
management stay on desktop. `hubReceivedAt` belongs to the desktop's composed
local sync view, not a new Hub endpoint or phone upload timestamp.

The desktop's export privacy fix does not add titles to the Hub: existing Hub
ingress strips session text. Android retains its optional-title fallback and
local visibility setting for older or other compatible snapshots. Desktop JSON
export and large-transcript fixes remain outside the phone's read-only contract.

## v0.67.0 titles, MiMo products and shared settings

The existing read routes, Bearer authentication and stream-v2 envelope remain
unchanged. The additive `syncSettingsRevisions` marker is tolerated in complete
stats; a shared-settings notification still carries a complete snapshot.
Android does not fetch or edit the new shared alias/pricing documents. It
continues to display the Hub's reported model names and costs.

Session titles now require server permission and per-device desktop consent.
The isolated released-Hub check verifies default stripping, opt-in delivery,
revocation clearing stored titles, and prompt/message-body exclusion. Android
displays only received titles, subject to its local visibility preference;
an untitled replacement snapshot restores the client/model fallback. Android
does not negotiate consent or write title policies. Hiding titles locally is
not a cache-erasure control.

MiMo `accountLabel` distinguishes Console from Desktop Membership (and legacy
Membership). `accountKey` keeps products with the same display name separate.
Console wallet windows remain native-currency balances with no invented quota
percentage. Optional `balance` spend fields retain their reported currency;
today/week readings are explicitly labeled tracked, with `trackingSince`
when supplied. Month/all-time readings come from the provider. Missing,
negative or non-finite spend is not displayed. Membership's unlabeled weekly
window keeps the existing Weekly fallback.

CodeBuddy/WorkBuddy and Cherry Studio counters already include the desktop's
reasoning normalization. Android does not add those tokens again. Desktop
collectors, alias/pricing synchronization, title-sharing controls, export,
locale and window-management features remain desktop responsibilities.

## v0.68.0 observed usage and incomplete prices

The existing routes, Bearer authentication and stream-v2 envelope are unchanged.
Optional `unpricedTokens` identifies tokens without a known model price; `costUsd`
remains the known subtotal, not the price of all reported usage. Android retains
the count on periods, projects, sessions and daily/monthly history, including
`perClient` and `perModel` buckets. Period attribution uses `clientUnpricedTokens`,
`modelUnpricedTokens` and `clientModelUnpricedTokens`; a tool filter never borrows
global model attribution. Missing fields mean no reported unpriced count, and
zero cost by itself does not imply missing prices. Counts are bounded by their
reported token totals. History summary fields are tolerated; Android derives
its selected-range totals from the actual daily rows and live-day overlay.

Cost labels show the known subtotal plus the unpriced count. Widgets use `+ ?`
in their existing cost slots, or `— (?)` when all reported cost is unknown,
with an explicit unpriced count in the accessibility description. Cost heatmap
shading covers known subtotals; token shading still includes all reported usage.

A Codex session with both `usageSource: "codex-dots-local"` and
`usageCoverage: "observed-only"` is labeled **Dots · observed only**. It covers
local observations while the desktop was connected, not a complete history.
Android does not enable Dots, recollect usage or add reasoning tokens again.
Desktop normalization handles native-rollout precedence and deduplication.
Dots collection/visibility controls, MiMo cookie discovery, batch aliases and
Edge Dock changes remain desktop-only. Synthetic v0.68 fixtures and the isolated
released-Hub contract check cover the new fields and freshness retention.

## v0.69.0 session identity, relationships and activity

The routes, Bearer authentication and stream-v2 envelope are unchanged. Sessions
in `periods.today.sessions` and `periods.month.sessions` may carry the optional
fields below. An older Hub omits them and its rows display as before. None of
them carries conversation text, question content, call IDs or paths.

| Field | Meaning |
| --- | --- |
| `nativeSessionId` | Codex only: the conversation UUID from the first rollout record, shared by every continuation file of that conversation. The `client:sessionId` accounting key does not change. |
| `t3ThreadId` | T3 Code thread identity that joins continuations and delegated runs, including across providers. Equal values belong together. |
| `parentSessionId`, `parentClient` | The session that started a subagent or background review. `parentClient` names the parent's client and means the child's own client when blank; the Hub leaves it out in that case. Top-level sessions and forks I make myself have neither. |
| `liveActivity` | Claude Code and Codex only: `{ state, observedAt }` with `state` one of `running`, `waiting`, `idle` or `unknown`. |
| `waitingForInput` | Codex only: `true` while a native question call is unanswered. It is not an approval signal. |
| `models` | Per-model tokens, already on the wire. Android keeps only finite positive counts for grouped rows. |
| `grokBotSession` | Cursor: an explicit Grok Bot marker if a producer sends one. The v0.69.0 Hub does not forward it, so Android normally decides from the id and models. |

Android reads each field tolerantly. Strings are trimmed and blank when absent.
`liveActivity` is kept only for a known state with a non-blank `observedAt`.
`waitingForInput` and `grokBotSession` are true or false only when the JSON value
is a boolean, and unknown otherwise. The Hub itself discards a `nativeSessionId`
that is not a UUID or that conflicts across devices, and a `liveActivity` for any
client other than Claude Code and Codex.

Grouping is presentation only. Each member keeps its own usage and the period
totals are untouched; a group sums its members once and never adds the session a
link points to a second time.

- Codex rollout files that share a `nativeSessionId`, and children that name a
  `parentSessionId`, collect under the main session with combined usage.
  Background reviews group as **Codex Auto Review**.
- Sessions that share a `t3ThreadId` group first, then parent links are followed,
  so a T3 Code continuation or delegated run folds into its main session.
- A Cursor conversation whose ID starts with `sand-subagent-`, or that has
  positive tokens on a `grok-bot-*` model, is a Grok Bot record. All models in
  that conversation stay together, including Claude. The group counts activity
  records, not chat rooms or billable calls, and infers no parent conversation.

A session's activity state is decided in this order:

1. An archived session is idle.
2. A `liveActivity` observed within the last 30 seconds, and not in the future, wins
   for Claude Code and Codex unless its state is `unknown`.
3. Otherwise the transcript decides. `turnEnded: true` means finished while the
   session is recent. For Codex, `waitingForInput` within the last 10 minutes means
   waiting. Grok Bot usage is idle unless `turnEnded: false` explicitly reports
   an open turn. Any other session active within 10 minutes is running, and an older
   one is idle.

Waiting is shown as **Waiting for you** in Home and Sessions. The synthetic
v0.69.0 fixtures cover these shapes in the stats and devices responses and both
stream event forms. The released-Hub contract check ingests similar shapes into
the pinned Hub and confirms which fields survive and that question and activity
text stays out of the response.

## v0.70.0 CatPaw and MiniMax pay-as-you-go balances

The routes, Bearer authentication and stream-v2 envelope are unchanged, and
upstream's API document and Hub source are identical to v0.69.0. Two additions
reach the wire.

**CatPaw.** `catpaw` is a new tool ID with the label CatPaw. It is off by default
on desktop. Its models are not priced from the catalog, so usage normally
arrives with `costUsd: 0` and `unpricedTokens` equal to the tokens, in the same
period, session, history and attribution fields as any other tool; custom
pricing set on desktop can change that. CatPaw supplies no
project, title or context size, so those fields are blank. Android reads the ID
like any other tool and supplies the display name.

**MiniMax pay-as-you-go.** A MiniMax key set to API billing makes the `minimax`
limit provider report `accountLabel: "Pay-as-you-go"` and one window instead of
the Token Plan session and weekly windows:

| Field | Value |
| --- | --- |
| `kind`, `metric`, `label` | `billing`, `credits`, `Balance` |
| `remaining` | The spendable amount. Zero and negative amounts are real readings. |
| `currency` | `CNY` for the China region, `USD` for international. |
| `usedPercent`, `remainingPercent`, `resetsAt` | `null`. A balance has no percentage and no reset time. |
| `showMeter` | `true` on the wire, even though there is no percentage to draw. |

The Hub keeps one MiniMax account at a time, so a Token Plan key and a
pay-as-you-go key do not appear side by side. A response without a usable amount
gives no window and the status `unavailable`. Android keeps `remaining` as
sent, including its sign.

Other v0.70.0 changes do not alter the wire shape. Codex compressed session logs
now count on desktop, so Codex totals can be higher. Hermes reasoning tokens join
its history on desktop, and the desktop's build registry gains two build IDs.
StepFun sign-in renewal, the token number animation and the faster session lists
are desktop-side. The synthetic v0.70.0 fixtures and the isolated released-Hub
contract check cover the CatPaw client and the balance, including a zero and a
negative amount, and confirm that no key crosses the Hub.

## v0.71.0 Goose, Notion AI and WorkBuddy accounts

The read routes, authentication, stream envelope and session grouping are
unchanged. `goose` is a new client ID; Android keeps its reported usage, cost,
models and sessions without collecting or re-dating them on the phone.

`notion` is a Limits provider named **Notion AI**. Its included allowances use
the existing quota fields: **Rolling** is a `session` window of 360 minutes;
**Monthly** is a `billing` window. Both carry percentages, and a supplied
`resetsAt` is a reset, not an expiry. `planLabel` identifies Business or
Enterprise. The account key represents a verified user/workspace pair, so
different workspaces remain separate. Cookies, raw user/workspace IDs and
workspace names are not part of that reading.

WorkBuddy's known accounts remain separate even when one is stale or signed
out. Android shows their individual quotas and statuses. The optional combined
quota, browser sign-in and account management are desktop features; the Hub
does not send the combined display setting. I do not infer a combined quota
or add a sign-in path on Android.

The released-source check exercises Goose, normalized Notion allowances and
multiple WorkBuddy accounts. The Android fixtures also cover a monthly-only
allowance and a missing reset time. Pi/CodeBuddy transcript details, tray fixes
and Dots executor detection stay on desktop. Android still reads no transcripts.

## v0.72.0 Cursor unified plans and session metrics

The Hub's read routes and session wire fields are unchanged. Cursor unified
plans now export one **Included usage** window: `weekly` with an optional
weekly reset, or `billing` for an explicit monthly period. A missing weekly
reset must not borrow the billing reset. A positive **Prepaid balance** uses
`metric: credits`, `currency: USD`, `remaining`, and `showMeter: false`; it has
no percentage or reset. Zero, negative and missing prepaid amounts are omitted
by desktop. Legacy model, Grok Bot and on-demand windows remain separate from
the unified plan. Android continues to use the normalized windows as supplied.

Session rows sum the disjoint members' message, input, cache-read/cache-write,
timed-output and duration counters before calculating a grouped ratio. Each
member's timed output is capped by its output and excluded when its duration
is zero. Cache hit is read / (input + read + write); output speed is timed
output / timed seconds. Unreported cache counters stay unknown, not zero.
I don't infer the desktop renderer's cache-reported flag because the Hub does
not export it. A group keeps the main conversation title and latest member
time. The real-Hub harness and retained fixtures check these boundaries.

## v0.73.0 matched timing, Cursor quotas and title cards

Read routes and wire field names are unchanged. The collector now derives
`timedOutputTokens` from the same messages as `timedDurationMs`, including an
explicit zero for input-only timed messages. It folds timed reasoning into
output only for clients with disjoint output/reasoning buckets. Android uses
these supplied counters without reconstructing output from `tokenCoverage`;
older agents' approximate counters remain accepted as reported.
These are estimated output rates, not provider-side decoding measurements;
the source interval may include queueing, network or tool waiting.

Cursor's desktop probe now prefers current-period Included usage, including
zero, then the unified sand reading. A monthly summary is a fallback only for
an explicitly monthly plan. Android still reads normalized quota windows; it
does not make Cursor requests. The released-source harness stubs the HTTP layer
under the real probe, then sends its result through the Hub.

Complete snapshots replace corrected usage, including zero. Freshness updates
preserve that replacement and never restore earlier counters. Session archive
reconciliation remains on desktop. Claude Desktop usage is collected into
`claude`, and Windows Goose usage into `goose`, rather than new Android clients.

Oh My Pi may synchronize a title card beginning with a private-use font icon,
a space, and a one-to-six-character uppercase/digit code followed by a colon.
`sessionTitleForDisplay` hides that icon only for `omp`, retaining the code and
text. It never changes the stored title or bypasses title privacy. Transcript
bodies and tool records remain local to desktop.

## v0.74.0 through v0.76.0 projects, activity and quotas

The five read routes, Bearer authentication and stream-v2 envelope are unchanged.
Project rollups gain an optional `models` map keyed by normalized model ID. I
keep it in the wire adapter and domain model, and bound displayed shares to
the project total. Tokens outside the map remain **Unknown model**, including
all tokens in an older rollup that omits the field. Android never estimates a
model split from project cost or a period-wide model share.

Antigravity sessions can carry `parentSessionId`; the existing grouping logic
folds them under the visible parent and labels an orphan as a subagent. DeepSeek
Harness project attribution arrives through the existing project fields.
Grok reasoning joins output exactly once on desktop; Android reads those totals
without adding reasoning again. Repeated daily-history merges retain explicit
`unclassifiedTokens: 0` so classified input remains classified in rolling ranges.

Grok Bot identity survives model aliases and different period copies. Recent
cloud usage alone cannot prove that a bot is running. An explicit open turn
is required; a recent ended turn still reads Finished.

Antigravity's **Gemini 4 Argon** allowance is a separate normalized window.
Missing percentages remain null and can carry `showMeter: false`, while a real
zero remains exhausted. Limits labels an absent value **Not reported**, not
Available. The contract harness runs the released quota mapper and pool reducer
with synthetic probe results; it never reads an account or starts a collector.

Home list counts are local presentation preferences, not Hub settings. Desktop
notch/dock behavior, collection priority and quota-probe fixes remain on desktop.
