# Architecture

## Product boundary

Token Monitor for Android is a lightweight, read-only window into one or more
desktop Token Monitor Hubs. It presents totals, limits, tools, models, devices,
projects, sessions, subscriptions, service status, and history without becoming
another collector or a remote-control client.

The desktop remains the source of truth. Pairing uses a private Tailscale address
and Hub secret, with an explicitly enabled home Wi-Fi address as an optional
fallback. A separate, off-by-default internet setting permits an existing
Hub at a public DNS name over HTTPS. It does not deploy a relay or expose a
desktop. The same read-only repository and parser serve all routes.

## Responsibility split

Desktop Token Monitor discovers local tools, parses their files, estimates
cost, checks provider limits, and aggregates device history. The Android app
does none of that. It reads the Hub over the selected route and presents
the synchronized result. This keeps the phone light and avoids duplicating the
desktop's adapters for dozens of tools.

```text
Desktop collectors → Token Monitor Hub → Tailscale, home Wi-Fi or opt-in HTTPS → HubRepository → HubSnapshot → Compose views
```

## Layers

```text
ui/         App shell, screens, presentation rules, ViewModel, ActivityHeatmap
domain/     HubSnapshot, UsagePeriod, DeviceUsage, LimitAccount, HistoryPoint, ...
data/       HubRepository
  protocol/ HubDtos, HubProtocolParser          complete wire → domain
             HubStreamProtocol                  SSE event → complete wire
  network/  HubApiClient, HubAddressValidator, EndpointFailover, HubDiscovery, BackoffPolicy, ServiceStatusClient
  storage/  SecureConnectionStore, SnapshotCache, DisplayPreferences
  update/   ReleaseUpdates (published GitHub release check and verified APK)
widget/     Responsive RemoteViews, cached-snapshot pages, explicit Live service and controls
```

Screens receive domain models and callbacks;
they never see JSON field names, headers, or storage. The stable parser owns the
complete wire shape; the stream reducer owns incremental delivery. A desktop
protocol change is absorbed in `protocol/`, its versioned fixtures, and focused
presentation helpers before any screen needs to change.

Explicit unpriced-token counts stay separate from known cost subtotals through
the parser, live-day history overlay and selected-range aggregation. Shared cost
formatting supplies the full warning in app rows and a compact marker in existing
widget slots. Optional Dots coverage maps to a session flag; it does not introduce
a phone collector, new permission or another connection owner.

New API account billing periods map to the existing Limits spend rows. They keep
their native currency and are distinguished from MiMo's locally tracked daily
and weekly deltas. Missing or invalid values stay absent; reported zero is retained.

## Local presentation in the r2 candidate

The parser retains subscription kind, payment ledgers, bindings, renewal/end
dates and notes, plus optional provider reset-credit/grant and usage-summary
blocks. Missing fields remain absent. Top-up records use their dated payments,
not the normalizer's recurring-subscription default fields.

`DisplayPreferences` owns tool hide/pin/order, quota visibility, currency and
optional live-rate settings. Keys are stable protocol/local identifiers; their
translated labels are never written back as identifiers. Filtering copies the
presentation lists and keeps raw Hub totals intact. The Pages widget consumes
the same saved display choices without changing `WidgetDeckGrid` geometry.

Usage estimates remain USD internally. `UsageCurrency` applies a valid saved
automatic or manual conversion at display time; a missing rate falls back to
USD. Provider balances and recorded payments keep their reported currency.
`ExchangeRateClient` reads the desktop's public rate sources with bounded
timeouts and response size, no redirects and no Hub information. Automatic
refresh belongs to the visible app; widgets only read saved rates.

`LiveThroughputTracker` compares matched cumulative token/output/duration
counters, re-baselines on source/capability changes and discards regressed or
idle samples. The optional panel starts Off, expires samples after eight
seconds and does not create a faster Hub request or processing loop.

`AppLanguage` uses Android 13's `LocaleManager` and a private language preference
with wrapped contexts on Android 8–12. Activity recreation keeps repository
ownership in the existing ViewModel lifecycle. Explicit resource IDs translate
app copy; Hub names, notes, titles and protocol keys stay as received. Widgets
redraw from the cache on a language change. Packs are bundled and use platform
font fallback; there is no runtime translation request.

`DiagnosticReport` constructs a fixed allowlist of version/state fields. It
does not serialize repository state, errors, URLs or cached payloads. The UI
requires preview followed by an explicit copy/share action.

## Connection lifecycle

`MainActivity` forwards resume and pause to `DashboardViewModel`, which tells
`HubRepository` whether that dashboard is visible, using a token unique to each
ViewModel. Pausing or clearing an older screen releases only its own token, so it
cannot stop a replacement screen's stream. Settings does not count as a
dashboard. Opening it releases that screen's ownership; an explicit widget session
can keep the same shared repository active on its lighter polling mode.

Refresh reapplies the calling screen's resumed dashboard state before restarting
the shared connection once. A paused screen or Settings cannot acquire streaming
ownership through Refresh. The last dashboard release still stops networking or
returns it to the explicit widget session's polling mode.

When a dashboard becomes visible the repository:

1. Reads a full snapshot: `/api/health` (unauthenticated identity check), then
   `/api/stats`, `/api/devices`, `/api/history`, `/api/subscriptions` with the
   bearer secret. A 404 on an optional endpoint is tolerated; a redirect is
   refused so the secret cannot be sent elsewhere.
2. Opens `/api/stats/stream` with `x-token-monitor-stream: 2`. Complete
   `snapshot` and `stats` events replace the wire snapshot. Small `freshness`
   events are merged by `HubStreamProtocol`, which updates only timestamp and
   stale metadata while retaining periods, sessions, providers, and device history.
   These events reuse the decoded usage data. Bounded metadata patches are kept
   alongside the last complete wire body and materialized on IO for cache writes.
   New devices or expanded device/limit fields use the complete decoder instead.
   Complete events are decoded directly from their original body or SSE envelope;
   normalization is deferred to cache persistence. JSON serialization uses a
   streaming writer rather than rebuilding text separately for every nested node.
   While usage is changing the Hub sends a complete `stats` event every few
   seconds, so `StreamUpdateCoalescer` applies at most one complete event per
   five seconds. The first after a quiet period applies at once; the newest
   later one waits for the interval and replaces any older one still waiting.
   Freshness events received while one waits apply after it, in order. A
   `snapshot` event, a closed stream or a paused dashboard discards the held
   event. This reduces decoding and UI work, not the bytes received.
3. On stream failure, reports "Live updates paused", waits according to
   `BackoffPolicy` (bounded exponential with jitter), and reconnects while the
   dashboard is still visible.

When neither dashboard nor widget session needs updates, foreground network work
stops immediately and the pending cache write is flushed. The separate optional
background job can later take one bounded snapshot, as described below. Returning to the app repeats
step 1 before streaming resumes. Cached data remains visible while that request
completes.

State changes are serialized on the main dispatcher; sockets, snapshot parsing,
and queued cache writes use IO dispatchers. Each shared repository run owns a closable
HTTP client and a generation token so cancelled work cannot replace newer state.
A callback active only while a dashboard or explicit widget session needs it restarts the connection when routes change.
Reconnections fetch a full snapshot to recover missed history. Subscription
refreshes are single-flight and throttle retries of a failed version.

Startup reads and decodes the compressed snapshot on IO. Saved pairing state is
available immediately, and cached activity appears when loading completes. A
delayed cache read cannot replace a newer network snapshot or restore a pairing
that was changed or disconnected during loading.

Complete updates reconcile equal periods, sessions, projects, limits and history
on IO before publication. Unchanged data retains its existing references, so
Compose does not repeatedly compare independently decoded session collections.

Widget cache saves refresh the launcher only when no Live or Refresh collector
owns rendering. Explicit session stop and disconnect updates still redraw. A
coordinated refresh shares one snapshot read/decode across both widget types;
the pages widget prepares its data once for all instances in that refresh.

### Two addresses, one connection

`HubConnection` holds a primary address and an optional home Wi-Fi address.
`EndpointFailover` orders candidates with the address that answered last
first, tries the next when one is silent, and stops immediately on a rejected
secret because another address cannot fix that. Snapshot reads use the
ordered list. When a live stream drops, the address that was answering is tried
first again; only a failed connection moves the other address to the front.
`HubRouteGuard` drops routes the phone cannot use right now (Tailscale without a
VPN, a home address on mobile data without a VPN) and checks that private names
resolve inside their range before any request.
The active address is surfaced to the UI so the header and Hub status can say
when the home route is in use.

The internet setting is persisted inside the encrypted pairing. Legacy records
without it decode as false. Public names gain HTTPS and port 443; Tailscale and
home routes retain their existing scheme and port defaults. Saving, the route
guard and the HTTP client independently revalidate targets before a request.
The internet setting cannot authorize a public address in the home fallback slot.
Android network security config trusts system CAs only. It permits cleartext for
private IP routes; code enforces HTTPS on internet routes. TLS failure messages
are shared by pairing and foreground delivery without exposing raw errors.

On API 37 and newer, `LocalNetworkAccess` excludes LAN routes unless Android's
local-network permission is granted. Tailscale remains eligible independently.
The UI requests permission only after a LAN action and resumes that action after
the Activity is resumed. Each HTTP request rechecks permission and run ownership.
Home-address repair verifies only the proposed address with the stored secret,
then saves that fallback while preserving the rest of the pairing.

### Finding a Hub on Wi-Fi

`HubDiscovery` enumerates the phone's private IPv4 addresses, derives up to two
`/24` prefixes, and probes each host's `/api/health` with a short timeout and
bounded parallelism. It runs only on a tap of Find, touches only
private ranges, and never sends the secret.

A discovery result fills the form but does not change stored pairing. The
explicit test-and-save action is the boundary between discovery and persistence.

## Storage

| Store | Content | Mechanism |
| --- | --- | --- |
| `SecureConnectionStore` | Addresses, secret, private-network choice | AES-GCM with a non-exportable Android Keystore key; ciphertext in app-private preferences |
| `SnapshotCache` | Last successful wire snapshot | One gzip file in private storage, written atomically; migrates the first release's preferences store on first read |
| `DisplayPreferences` | Non-secret display choices | Plain app-private preferences, exposed as a `StateFlow` |
| `WidgetRefreshPreferences` | Background consent, interval, Wi-Fi choice and run metadata | Plain app-private preferences; no Hub addresses, credentials or response contents |

Backup and device-transfer rules exclude all four, so a phone migration does
not carry the pairing with it.

Cache writes during foreground updates are coalesced to at most one per minute,
plus one when the app pauses. `SerialDiskQueue` preserves their order and keeps
repository shutdown behind the final save or clear operation.

Cache readers and writers share a process lock around `AtomicFile`. Cache reads
accept older SSE envelopes, and foreground writes store the normalized stats
object. The responsive widget updates after a saved snapshot, when resized, and
after each stats refresh during an explicit Live session; Android 12+ selects
compact, medium, or large RemoteViews using responsive size mappings. Older
Android versions receive portrait and landscape layouts. A separate fixed-layout
pages provider renders Overview, Limits, Breakdown, and Activity from one current
cache/session snapshot. It sends the launcher one complete 1.82:1 page bitmap
plus transparent native touch targets for Previous, Next, Open, Refresh, and
Live. It requests 4×2 but never switches composition when a launcher allocates a
different physical size. The selected page is stored locally per widget. This
avoids launcher collection transforms and keeps page changes local without fetching.
Neither provider registers a periodic widget update. On the background-refresh
branch, one separate persisted JobScheduler job supports explicit 30/60-minute
consent. It uses the shared repository for a bounded health/stats/history read,
never SSE, with main-thread admission and cancellation before foreground work.
Non-secret preferences retain consent, authentication rejection, failure age and
received response bytes. A failed run preserves the cache and marks it stale;
an older queued write cannot clear a later rejection. The job cancels when its
widget/pairing/consent requirements stop being true.
See [Android widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts).

## Presentation

`DashboardViewModel` owns the current destination, the return destination for
Settings, the connection form state, the display preferences, provider status
loading, and Wi-Fi discovery. It exposes everything as `StateFlow`s.

The Compose presentation layer renders the app from those flows. Its files are
split so that each file has one job. `TokenMonitorApp.kt` sets up theme and
motion and hosts the scaffold; `DashboardChrome.kt` and `Menus.kt` are the shell
around the content; `DashboardContent.kt` routes the selected view to one of the
screen files (`HomeScreen`, `BreakdownScreen`, `LimitsScreen`, `DevicesScreen`,
`ProjectsScreen`, `StatusScreen`, `TrendsScreen`), which share `DetailRows.kt`,
`Primitives.kt`, and `ActivityHeatmapGrid.kt`; `ConnectionSettings.kt` owns pairing
and settings. Shared presentation rules live alongside them in focused files:

- **Palette and typography.** `Theme.kt` holds `InterfaceTheme` (TM1's four
  desktop-customizable colors and TM2's additional chart color) and
  `Palette`, every color the app draws, resolved from a theme the way the
  desktop stylesheet does it: a light background flips the overlay, line,
  panel, and sunken surfaces, and semantic colors stay fixed. The palette is
  provided through `LocalPalette`; semantic color getters and typography live
  in `Theme.kt`. Typography is built once per text size setting. Widget RemoteViews
  resolve this same Palette; the theme preference changes both surfaces. A local
  widget redraw applies new colors without enabling Live or fetching data.
  A custom chart color changes K-line up candles, seven-day widget bars and
  active heat steps, not attributed bar-series or vendor colors. Default chart
  codes retain each preset's established bar color and heat ramp.
- **Time.** `LocalNow` is a composition local updated every thirty seconds.
  Countdowns (`formatReset`) and relative ages (`formatRelativeAge`) read it,
  so labels stay accurate between Hub events without per-row timers.
- **Motion.** `LocalInteractionMotion` resolves the reduce-motion setting
  against the system animator scale. Every animation reads it and collapses
  to zero duration when motion is off. `DesktopEaseOut` is the desktop's
  easing curve. Token totals follow the desktop's token number animation:
  `DigitRoll.kt` ports its digit roll for the headline and breakdown rows, and
  Count up keeps the earlier rolling headline value. Bars and charts grow in on
  open, chevrons rotate, and views cross-fade with a small slide.
- **Menus.** `AnchoredMenu` is a popup positioned a few pixels off its anchor,
  replacing the stock menu whose screen-edge margin left a gap above the view
  switcher.
- **Periods.** `UsageAggregation.kt` maps Day, Month, and Total to Hub periods and
  computes Week, 7-day, and 30-day ranges from daily history plus today's live
  period. Rolling ranges have no sessions or projects because the Hub reports
  those only for its own periods.
- **Formatting.** `UsageFormatting.kt` centralizes cached formatters and stable
  labels; `VendorPresentation.kt` maps tool and model names to marks and colors;
  `SessionPresentation.kt` owns the Running, Finished, and Idle window plus
  context-window calculations. Those rules are unit-tested without Compose.
  Its OMP title-card cleanup is presentation-only: stored text stays intact,
  and the same title helper is used by Home and expanded/grouped session rows.
- **Home sessions.**
  `HomeSessions.kt` derives the five most recent sessions plus running overflow
  from month/today, deduplicated by client and ID. It uses the shared activity,
  context and slow clock helpers rather than new timers or requests. Archived,
  deleted and source-deleted wire flags map to one idle domain state. Existing
  stored Home module choices are retained when new defaults are added.
  Session speed/cache-hit readings and optional cache estimates share the same
  presentation helpers with Sessions. A local title toggle affects both views
  without changing cached wire data. No new background work is added.
  `SessionMetrics.kt` sums disjoint members' raw counters before calculating
  group cache-hit and output-speed ratios. Untimed output is excluded per
  member, not capped after aggregation. `SessionGrouping.kt` keeps the main
  title but uses the latest member time and combined message count. The Home
  and Sessions views share these readings; context and cache-TTL estimates
  remain individual-session information.
  Grouping and sorting run on a background dispatcher only when the month or
  today session lists change. The slow clock still updates row activity and
  running overflow without regrouping the lists.
  Home receives the two session lists directly. Preparation and prepared-row
  keys compare their references; changed lists are grouped on the background
  dispatcher and publish without a structural comparison on the main thread.

- **Projects and Home counts.** Optional project model maps pass through the
  wire adapter into `ProjectUsage`. `ProjectBreakdown` bounds each share and
  retains an explicit unknown remainder; expanded rows choose Tools or Models.
  `DisplayPreferences` stores independent 1–50 Home list counts locally, without
  changing saved module visibility/order, pairing, or widget composition.

- **Settings.** `ConnectionSettings.kt` sections collapse to a header with a summary,
  like the desktop settings list. A fresh install shows `WelcomeSetup` instead
  of Settings until a Hub is saved.

## Heatmap geometry

`ActivityHeatmap.kt` computes the rolling twelve-month contribution grid and
its intensity ramp outside Compose so partial or duplicated history is handled
deterministically and unit-tested. The Compose layer only draws the cells.
Home prepares this data when its Activity item enters the lazy list; other
dashboard screens do not prepare a hidden Home heatmap. Home and Trends merge
daily history on a background dispatcher, retaining the last result while new
usage is prepared. Their cache inputs include attribution and the source date,
but exclude session changes and transport timestamps within that date.

## Deliberate limits

- The Hub does not synchronize prompt or response bodies, so the phone cannot
  render session transcripts.
- The app is read-only: no ingest, no account switching, no subscription
  edits, no device deletion.
- Desktop appearance, window, tray, collection, and credential settings remain
  desktop responsibilities.
- Widget Live is an explicit, bounded foreground service with a Stop notification.
  Background refresh is a separate opt-in periodic job; only that job is restored
  by Android after reboot. No app wake lock, boot receiver or analytics.

## Widget Live and motion

The dashboard and service acquire a main-thread reference-counted repository. A
visible dashboard uses the Hub's SSE stream for immediate updates. Widget Live uses
one authenticated `/api/stats` read every 30 seconds after its initial snapshot,
avoiding a full stream frame for every desktop ingest. Opening or closing the
dashboard switches delivery modes without running both. Turning Live off releases
the widget lease; changing pairing or disconnecting first cancels the session.

The service is START_NOT_STICKY, has a one-hour monotonic expiry, handles Android's
dataSync timeout callback, and does not request a wake lock or battery exemption.
Its absolute `elapsedRealtime` deadline includes deep sleep. Timed checks and a
screen-on callback stop expired sessions; every widget-owned request also checks
the deadline before opening a socket. Refresh does not extend an existing Live
deadline. An open dashboard can continue its independent foreground connection.
Refresh uses the same service for one snapshot with a 45-second timeout. First use
requests notification permission so Stop remains available outside the launcher.
Android sleep and connectivity can delay data; the widget always includes its last
successful update time and labels reconnecting sessions separately from Live.
The notification is reposted only when text visible to the user changes.

The midnight layout uses the existing upstream vector assets. Counts use full Long
precision with grouping. A native ViewFlipper slides the whole total only when
new data changes it; app Reduce Motion switches to a static TextView. This is not
the Compose per-digit renderer. No intermediate token values or animation timer
are sent through AppWidgetManager. Resizing adds quota rows and a seven-day chart;
missing history observations are shown as gaps.

The responsive widget's chart bitmap uses the chart ImageView's measured pixel
size. An unattached copy of the configured native layout accounts for individual
dimension rounding, weighted rows and font-dependent placeholder text without
changing launcher geometry. Each refresh shares dimensions for matching layout
sizes across widget instances, then discards the cache. Background refreshes use
a private measurement Looper that is stopped and joined when the batch ends,
including on failure; measurement never waits for the main thread while holding
the widget coordinator lock. No hierarchy is attached to a window.
