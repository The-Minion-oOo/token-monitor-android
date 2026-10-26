# Security and privacy

## Optional display features in the r2 candidate

Automatic currency conversion contacts the same public currency services as
desktop: jsDelivr's `@fawazahmed0/currency-api` feed, with the project's
`currency-api.pages.dev` feed as fallback. Requests contain no Hub address,
secret, account or usage data. HTTPS uses system certificate authorities;
redirects are rejected, reads are bounded, and requests stop when the app leaves
the foreground. Widgets use saved rates. USD and manual-rate display need no
currency request.

The diagnostic report is an allowlist of versions, route category and state
flags/counters. It excludes addresses, secrets, account identities, raw errors,
conversation text and cached responses. Preview precedes copy/share; neither
action is automatic. Sharing uses Android's chooser. The chosen recipient or
clipboard reader receives the previewed report.

Language resources are bundled. App language, display currency, visibility and
tool ordering do not change Hub settings or provider credentials. Payment notes
and account bindings remain local snapshot content and may be private; they are
not included in diagnostic reports or widget labels.

## Network boundary

The app accepts Tailscale addresses by default and recommends that route. Private
LAN addresses require an explicit setting. A separate **Allow an internet Hub over
HTTPS** setting starts off, including for existing pairings. It permits public DNS
names only over HTTPS. Saving, the route guard and the HTTP client each enforce
that choice and refuse public HTTP before opening a request, including for stale
or altered saved addresses. IP literals, single-label names and private-use names
are not internet Hub targets. The home Wi-Fi fallback remains private-only.

The internet option connects to an already available HTTPS Hub, such as the
official Cloudflare Worker or a Node Hub behind an HTTPS reverse proxy. It does
not deploy a Hub, open a port or change desktop settings. TLS uses Android's
system certificate store only, without user-installed CAs or Hub certificate
pinning. Certificate and hostname failures stop the TLS handshake before HTTP
credentials are sent. The app reports those failures without printing secrets.

The network security configuration allows cleartext for existing Tailscale and
LAN IP routes. Android's domain rules cannot scope that permission by IP subnet;
the app therefore enforces HTTPS for internet routes in code at all three
boundaries. No debug trust override broadens the system trust store.

The Hub secret can also change Hub data, although Android only reads it. Use a
long random secret. If a phone is lost, change the secret on the Hub and every
connected desktop, then pair the phone again.

[Tailscale](https://tailscale.com/docs/reference/connection-types) normally
attempts a direct device-to-device connection and can fall back to an
end-to-end encrypted relay when network conditions require it. The app does not
operate a cloud relay or require the desktop Hub to be exposed on the public
internet.

The desktop Hub serves plain HTTP. Over Tailscale the traffic is encrypted by
Tailscale's WireGuard tunnel. On the optional home-network (LAN) route it is not
encrypted, so other devices on the same Wi-Fi could observe the Hub secret and
usage data. Use the LAN route only on a network you trust, and prefer Tailscale.

Before any request, including the identity check, the app confirms the route
fits the phone's current networks. A Tailscale address or MagicDNS name is
tried only while a VPN is connected, because without Tailscale the 100.64/10
range belongs to carrier-grade NAT. A home address is tried on Wi-Fi or
Ethernet, or through a VPN such as a Tailscale subnet router, and never directly
over mobile data. `.ts.net` names must resolve to Tailscale addresses and
`.local` names to private IPv4 addresses, so a hostile DNS answer cannot move
the secret to a public host.

The desktop Hub secret is sent as a bearer credential only to the validated Hub address. It is never logged or included in diagnostics.

The unauthenticated health response must identify a Token Monitor Hub before the
first protected request is sent. HTTP redirects are rejected so an authenticated
request cannot move the bearer secret to another host.

## Local storage

- The Hub URL and secret are encrypted with a non-exportable Android Keystore key.
- The app-private snapshot cache stores compressed Hub JSON responses, including usage and account/device/project metadata returned by the Hub. It is not separately encrypted by the app; Android app isolation protects access. Pairing credentials use the separate Keystore-backed store.
- Android backup/data-transfer rules exclude pairing and snapshot storage.
- Background-refresh preferences hold only consent, interval, Wi-Fi choice,
  success/failure timestamps, secret-rejection state and response-byte metadata.
  They contain no Hub address, secret or response content and are excluded from
  backup and transfer by the shared-preference exclusion.
- A secret typed into the connection form is held only in memory, not saved in
  Activity instance state. Home-address repair reads the existing encrypted
  pairing and does not expose its secret back to the form.

## Read-only API use

The app calls only:

- `GET /api/health`
- `GET /api/stats`
- `GET /api/devices`
- `GET /api/history`
- `GET /api/subscriptions`
- `GET /api/stats/stream`

It does not call ingest, subscription mutation, or device deletion routes.

Two optional connection features operate only within private address ranges:

- The **home Wi-Fi fallback** is a second saved address that must pass the
  same private-network validation. The secret is sent only to whichever of the
  two saved addresses identifies itself as a Hub.
- **Find** searches the private `/24` network of the phone's current Wi-Fi or
  Ethernet connection (never mobile data or a VPN) for a Hub by reading the
  unauthenticated `/api/health` endpoint. It runs only when tapped, never sends
  the secret, and stops as soon as one Hub answers.

The Status view separately reads public service-health endpoints for Claude,
OpenAI, Cursor, and DeepSeek. Those requests do not include Hub credentials
or Token Monitor usage data.

Opening App updates checks this app's latest published GitHub release. A
download starts only after the user taps Download and install. These HTTPS
requests do not include the Hub secret or usage data. Before handing the APK to
Android, the app verifies the release checksum, package identity, version code,
and pinned release signing certificate. Android controls installation approval.

## Lifecycle and resource use

### Background widget refresh (in development)

I am developing background refresh on `feature/widget-background-refresh`;
it is not included in the published r3 release. Its security boundary permits one
periodic JobScheduler job only after choosing 30 or 60 minutes. The default is
Off, with Wi-Fi only enabled. A placed widget, saved pairing and a secret that
has not been rejected are required; losing any requirement cancels the job.

Each run must finish within 45 seconds, skip an active dashboard or widget
Live/Refresh session, and use the existing validated read-only Hub routes. It
must not open an SSE stream, display a notification, acquire an app wake lock,
or add a dependency. Failure keeps the last successful snapshot and marks it
stale; secret rejection stops scheduling until pairing is successfully verified.
Wi-Fi only must prevent cellular requests, including when Tailscale is active.
The run decides from the physical network under the VPN, not from JobScheduler's
network type, so a VPN that reports itself as metered cannot block or permit it.

The persisted job requires `RECEIVE_BOOT_COMPLETED` solely because
Android requires that permission for JobScheduler to restore a persisted job
after reboot. Android restores the job; the app will register no boot receiver.
The job must recheck eligibility and accessible pairing before any request.
An overnight preview phone run used synthetic data and completed eight bounded
refreshes. The persisted job returned after reboot without opening the app and
completed a post-reboot read. Android's per-app power attribution is an estimate,
and a matching Off baseline was not measured.
No UI test helper or persistent instrumentation was used on the phone.

### Current foreground widget sessions

A user-started dataSync foreground service supports widget Live for one hour with
a 30-second refresh cadence, or a single refresh for up to 45 seconds. It shares
the dashboard repository, requests notification permission to expose Stop, and
stops without restarting after expiry, removal of the last widget, or a process
kill. These explicit sessions do not automatically restart. The separate opted-in
periodic job is described above. There is no app wake lock, boot receiver,
analytics SDK or advertising SDK.

`ACCESS_NETWORK_STATE` lets an active dashboard or widget session reconnect after a route changes.
On Android 17 and newer, `ACCESS_LOCAL_NETWORK` is requested for a chosen LAN
operation. Denial or revocation excludes home routes without erasing pairing;
Tailscale routes remain eligible. Discovery sends no secret. Saving a replacement
home address requires a successful authenticated check of that address.
The home-screen widget reads saved data while Live is off and receives current snapshots during an explicit session. It
shows saved totals, estimated cost, quota windows, and a small usage chart according
to its size. Adding it makes those figures visible on the launcher; account emails,
project labels, session identifiers, and connection credentials are omitted.

## Data visible on the phone

Hub responses can include device names, model/tool attribution, project folder labels, session identifiers and titles, account email/plan metadata, costs, and normalized limits. Conversation titles can reveal sensitive text and appear in Home and Sessions when the Hub provides them; they are not displayed on the home-screen widgets. Account emails are hidden by default and can be shown only through an explicit display setting. The Hub does not send absolute project paths or prompt/response transcript bodies.

When **Show session titles** is off, search also excludes title text. The cached
Hub response may still contain titles; hiding them does not delete stored data.

## Reporting a problem

Do not include a Hub secret, unredacted account email, raw Hub response, or private tailnet address in a public issue. Reproduce with the sanitized fixture Hub when possible.
