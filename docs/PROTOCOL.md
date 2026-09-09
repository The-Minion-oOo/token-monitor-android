# Hub protocol compatibility

This Android app treats the desktop Hub as an external, read-only protocol.

## Verified baseline

The baseline is upstream Token Monitor **v0.54.0**, released 2026-09-04. The
release and source were checked directly from the `Javis603/token-monitor`
tag before this implementation. The older local desktop checkout was not
changed and is not the protocol authority.

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
| `GET /api/stats/stream` | Bearer secret | SSE `snapshot` and `stats` frames while a dashboard is visible. |

The Hub also accepts `X-Token-Monitor-Secret`; this app intentionally uses one
authentication style only. It does not call the Hub's ingest, subscription
write, or device deletion endpoints.

## Errors and streaming

The verified Hub returns `{ "error": "unauthorized" }` with HTTP 401 for a
protected read without a valid secret, and `{ "error": "not_found" }` with
HTTP 404 for an unknown route. The app gives a specific safe message for those
conditions and does not surface request headers or the secret.

`/api/stats/stream` sends an initial `snapshot` event followed by `stats`
events whose data envelope contains `stats` and `at`. The Hub emits an SSE
comment heartbeat every 30 seconds. The app closes the underlying stream in
`onPause`, opens it only on a dashboard route, refreshes first on resume, and
retries a disconnected stream with 1–30 second bounded exponential backoff plus
jitter.

Streamed stats do not carry the full retained history embedded in the dedicated
device response. The repository therefore keeps the last authenticated device
history while applying live device totals. A foreground refresh replaces it
with the current `/api/devices` result.

## Fixtures

Sanitized v0.54.0 examples live in
`app/src/test/resources/protocol/v0.54.0/`. They contain no user secrets,
machine paths, account identifiers, or real usage. Parser tests cover every
read surface, the SSE envelope, omitted optional fields, and a future unknown
field. The compatibility parser maps only the fields the dashboard needs and
ignores the rest. v0.54.0 coverage includes project `tokens`, session counts
and times, device OS/cadence/history, limit labels and balances, and history
component provenance inside `perClient` and `perModel`.

When the desktop protocol changes, add a new versioned fixture directory and
tests before changing the Android mapping.
