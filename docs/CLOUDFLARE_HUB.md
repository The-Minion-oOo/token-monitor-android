# Cloudflare Hub: desktop to Android without Tailscale

Cloudflare can run a free-plan Token Monitor Hub that your Windows and Mac
desktops send usage to and your Android phone reads live over HTTPS. Desktops
make outbound connections, so you do not expose a computer's port or leave one
computer running as the Hub server. Tailscale is still recommended when you can
use it; this is our cloud-sync setup when you cannot.

I checked the desktop names and commands against **v0.81.0**, commit
`ce89af774664f3852aa605350b59732247699650`. English interface labels are used below.

## 1. Before you start

- Install [Token Monitor desktop v0.81.0](https://github.com/Javis603/token-monitor/releases/tag/v0.81.0)
  on each Windows or Mac computer you want to include.
- Install this repository's [Android APK](https://github.com/The-Minion-oOo/token-monitor-android/releases/latest).
  Internet HTTPS pairing requires Android app v0.78.0 r2 or newer.
- Have a free [Cloudflare account](https://dash.cloudflare.com/sign-up). The
  browser deployment also asks you to connect a GitHub or GitLab account for
  the copy of the Worker repository.
- Use a password manager to generate and save a long random **Hub secret**;
  for example, a randomly generated 64-character password. Use this same secret
  on the Worker, every desktop and Android. Do not reuse a provider API key.

`https://hub.example.com` below is an illustration, not a working Hub. Replace
it with the HTTPS Worker URL Cloudflare gives you. You do not need to buy a domain.

## 2. Deploy the Worker Hub

The upstream button imports **`main`**, which can be newer than v0.81.0. The
manual method below deploys the exact v0.81.0 release instead.

1. Open **[Deploy to Cloudflare](https://deploy.workers.cloudflare.com/?url=https://github.com/Javis603/token-monitor/tree/main/worker)**,
   the button linked from the desktop release's README.
2. Sign in to Cloudflare and connect the Git account when prompted. Choose the
   repository copy and Worker name. Keep a note of the Worker name for updates.
3. Set **`TOKEN_MONITOR_SECRET`** to your saved random Hub secret when setup asks
   for it. Leave **`PUBLIC_STATS_ENABLED`** blank/unset; Android does not need
   public stats. Complete the deployment and wait for it to succeed.
4. Copy the Worker's production **HTTPS URL** from Cloudflare. Use the Worker
   address itself, without `/api/stats`, a secret in the URL or a preview URL.
5. Open that address with `/api/health` appended in a browser. The JSON should
   include `"ok": true`, `"role": "hub"`, `"runtime": "cloudflare-worker"` and
   `"secretRequired": true`. `"deviceCount": 0` is normal before a desktop uploads.
   This health check does not require your secret.

The supplied `worker/wrangler.toml` includes the **`HUB`** Durable Object binding,
**`HubDO`** class and SQLite migration. Deployment provisions that storage; you
do not separately create a KV namespace, D1 database or storage account. Keep
the supplied binding and migration configuration intact.

In the Worker's **Settings → Variables and Secrets**, check that
`TOKEN_MONITOR_SECRET` is stored as type **Secret**. If it is missing, or health
says `"secretRequired": false`, choose **Add**, type **Secret**, name it
`TOKEN_MONITOR_SECRET`, enter your saved value, then choose **Deploy**. To reach
these settings, open **Workers & Pages** and select your Worker. If the existing
value is plain text, replace that entry with a Secret of the same name and value.
The Worker refuses private data routes with `503 secret_required` until a
nonblank secret is set. See Cloudflare's
[secret instructions](https://developers.cloudflare.com/workers/configuration/secrets/).

### If the one-click deployment fails

The upstream Worker README documents a configuration-parse failure and a
deployment that returns only `Hello world`. Retry in a private/incognito browser
window; if it still fails, use the release-pinned manual route below.

1. Install [Node.js 22 or newer](https://nodejs.org/en/download/) if needed.
2. Download the [v0.81.0 source ZIP](https://github.com/Javis603/token-monitor/archive/refs/tags/v0.81.0.zip)
   and extract it. This is the source archive, not the desktop installer.
3. Open PowerShell on Windows or Terminal on Mac. Use `cd` to enter the
   extracted source's `worker` folder: for example,
   `cd "C:\path\to\extracted\worker"` on Windows or
   `cd "/path/to/extracted/worker"` on Mac. Replace the example path with your
   actual folder. It must contain `package.json`, `wrangler.toml` and `src/index.js`.
4. If the button already created your Worker, edit only the `name` at the top of
   this copy's `wrangler.toml` to match that existing Worker. Otherwise, keep the
   default `token-monitor-hub`. Keep the storage binding and migration entries.
5. Run these commands, one at a time, from that `worker` folder:

   ```sh
   npm install
   npx wrangler login
   npx wrangler secret put TOKEN_MONITOR_SECRET
   npx wrangler deploy
   ```

   Authorize your Cloudflare account in the browser for `login`. Paste your
   saved Hub secret at the `secret put` prompt, not into a command or source file.
   If Wrangler asks to create the named Worker, check the name before accepting.
   In PowerShell, if an `npm.ps1` or `npx.ps1` execution-policy error blocks a
   command, use `npm.cmd` or `npx.cmd` with the same arguments.
6. Copy the deployed HTTPS URL printed by Wrangler and repeat the `/api/health`
   check above. Keep this source folder for redeployment.

If `Hello world` persists, check that the deployment uses the complete `worker`
source, not an imported repository containing only a README and Wrangler config.

## 3. Connect every desktop

Repeat these steps on **each** Windows or Mac desktop, including the computer
you used to deploy. None of them needs to host the Hub locally.

1. Open Token Monitor desktop's **Settings → Multi-device Sync**.
2. Set **Sync method** to **Connect to a hub**.
3. Enter the copied HTTPS Worker address in **Hub URL** and your saved Hub
   secret in **Secret**. If a connection is already saved, choose **Change
   connection** to show the fields.
4. Choose **Save**. Look for **Connected** and a **Last upload** status in the
   connection panel. Check that this desktop appears in its device list.
5. Set **Sync upload frequency** to **Live** for live uploads; this choice saves
   automatically. A fixed interval intentionally delays new desktop uploads.

Keep Token Monitor running on a desktop while it collects and uploads new
usage. The cloud Hub stays available when that desktop sleeps or shuts down, but
it cannot collect new usage from a computer that is offline.

## 4. Pair the Android app

1. Open Token Monitor on Android. A new installation opens the connection
   screen; scroll past its Tailscale introduction to the connection form. If
   already paired, open **Settings → Connection**.
2. Scroll below the address and secret fields and turn on **Allow an internet
   Hub over HTTPS**. It starts off. Return to the address field, now labelled
   **Hub address**.
3. Enter the HTTPS Worker base address, for example `https://hub.example.com`,
   and paste the same secret into **Hub secret**. Leave the optional home Wi-Fi
   address empty for this setup.
4. Tap **CONNECT** on the initial pairing screen, or **CHECK AND SAVE CONNECTION**
   when replacing an existing connection.
5. The dashboard opens after verification. The route is **Internet (HTTPS)**;
   it is also shown under **Settings → Hub status**. Check **Devices** for the
   desktops that uploaded in step 3.

With the internet switch on, a bare public name such as `hub.example.com` gains
`https://` automatically and uses port **443** by default. An explicit
`http://` address is refused. Enter the base address only, without an API path,
query string or secret in the URL. Do not enter an IP address.

The dashboard receives live updates while visible. Widget Live and periodic
widget refresh remain separate, optional choices; see [Widgets](WIDGETS.md).

## 5. Keep it safe

- The shared secret authorizes reads **and changes** to Hub data, even though
  Android only reads. Use a long random value and keep it in your password manager.
- If the phone is lost, replace `TOKEN_MONITOR_SECRET` on the Worker through
  **Settings → Variables and Secrets**, deploy the change, then update **Secret**
  on **every desktop** and re-pair any Android phone you still use. The old secret
  must stop working; changing only the phone's saved secret does not revoke it.
- Leave **`PUBLIC_STATS_ENABLED` unset** unless you deliberately want public
  aggregate stats. It is not required for desktop or Android sync.
- Never use plain-HTTP port forwarding. This setup uses HTTPS on Cloudflare
  and outbound connections from desktops.

## 6. Update the existing Worker when prompted

The browser button creates a standalone copy; updating the desktop app does
not update that copy. In **Multi-device Sync**, the desktop can show:

> Cloudflare Worker update available — redeploy to get the latest Hub build

This compares the Hub build with the desktop's bundled Hub code, not just the
desktop version number. A version-only desktop update need not require a redeploy.

1. Get the source for the desktop release you have updated to. Use the manual
   procedure above with that release's tag and deployment instructions, not
   automatically the old v0.81.0 ZIP.
2. In its `worker/wrangler.toml`, keep the **existing Worker name** and preserve
   the existing storage binding/migration configuration. Follow any storage
   migration instructions in that release; do not create a replacement Hub.
3. From that `worker` folder, run `npm install`, `npx wrangler login` and
   `npx wrangler deploy`. Keep the existing `TOKEN_MONITOR_SECRET`; do not
   generate a new one for an ordinary update. The supplied `keep_vars = true`
   preserves dashboard-managed variables; values explicitly declared in the
   config still take precedence.
4. Repeat the health check, reopen desktop **Multi-device Sync**, and confirm
   **Cloudflare Worker is up to date** and desktop uploads. Open Android and
   confirm the existing devices are still present. An unchanged URL and secret
   do not require pairing again.

If using the repository created by one-click deploy instead, update that copy
with the new upstream Worker source before redeploying it through Workers Builds.
Redeploying its unchanged old code will not clear a genuine build-update notice.

## 7. Troubleshooting

These are the Android app's English messages; another app language translates them.

| Message or symptom | What to do |
| --- | --- |
| **Internet Hubs require https://. The secret was not sent.** | Replace explicit `http://` with the copied HTTPS Worker URL. |
| **Turn on Allow an internet Hub over HTTPS, or use Tailscale (recommended).** | Enable that switch before saving the public HTTPS address. |
| **The Hub certificate does not match its HTTPS address. The secret was not sent.** | Use the exact hostname covered by the certificate. Do not substitute an IP address. Fix a custom-domain certificate on Cloudflare. |
| **The Hub HTTPS certificate is not trusted or has expired. The secret was not sent.** | Check the phone's date/time and the Hub's certificate. Use a valid certificate trusted by Android's system store; installing a user CA is not a workaround. |
| **A secure HTTPS connection to the Hub could not be established. The secret was not sent.** | Check connectivity and the Worker's HTTPS configuration. Do not downgrade to HTTP or bypass TLS checks. |
| **The Hub rejected the secret. Check the connection settings.** (HTTP 401) | Compare the saved Worker secret with the value entered on Android; re-enter it and save. After rotation, update every desktop too. |
| **No devices have checked in to this Hub.** in Devices; **No devices have checked in** on Home | Pairing can succeed before any desktop uploads. Check each desktop's **Connect to a hub**, **Hub URL**, **Secret**, **Connected** and **Last upload** status. Choose **Live** or wait for its selected interval. |
| **Waiting for activity from your saved Hub.** | Check desktop uploads first. If health still shows zero devices, no desktop has checked in to this Worker yet. |
| **The Hub returned HTTP 503. Try again when it is online.** | A missing Worker secret produces `503 secret_required`. Check `TOKEN_MONITOR_SECRET` and deploy it; a 503 can also have other causes. |
| **The address is not a Token Monitor Hub.** | Confirm `/api/health` returns the Hub JSON, not `Hello world`, a sign-in page or a different service. Use the Worker base URL. |

Android refuses redirects and does not accept user-installed certificate
authorities. Fix the Worker URL, certificate or deployment rather than disabling
those protections. [More connection options](PAIRING.md#optional-internet-hub-over-https).

## Free-plan limits and source references

The upstream Worker documentation describes free-plan use for small setups.
It is not unlimited: uploads, connected readers and storage consume the plan's
allowances. Check Cloudflare's current [Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
and [Durable Objects pricing and free-plan limits](https://developers.cloudflare.com/durable-objects/platform/pricing/)
instead of relying on quota numbers copied into this guide.

Release-pinned sources: [Worker setup](https://github.com/Javis603/token-monitor/blob/v0.81.0/worker/README.md),
[Hub API](https://github.com/Javis603/token-monitor/blob/v0.81.0/docs/API.md),
[desktop configuration](https://github.com/Javis603/token-monitor/blob/v0.81.0/docs/configuration.md),
and [Worker storage configuration](https://github.com/Javis603/token-monitor/blob/v0.81.0/worker/wrangler.toml).
Cloudflare documents [one-click provisioning](https://developers.cloudflare.com/workers/platform/deploy-buttons/)
and [managing secrets](https://developers.cloudflare.com/workers/configuration/secrets/).
