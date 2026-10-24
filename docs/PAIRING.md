# Pairing with a desktop Hub

On the background-refresh feature branch, I keep scheduled widget reads separate
from pairing: they start Off, with Wi-Fi only on. Enabling internet access does
not enable scheduled refresh. A rejected saved secret stops the scheduled job
until successful verification; disconnecting cancels it immediately. Existing
Tailscale, home fallback, HTTPS and Hub identity protections apply to every run.
This option is not included in the published r3 APK.

## Recommended: Tailscale

Each person uses their own Tailscale account and tailnet. No shared server or project account is required.

1. Install Tailscale on the desktop and Android phone.
2. Sign both devices into the same tailnet.
3. Open Token Monitor desktop **Settings → Multi-device Sync**.
4. In the desktop app, choose **Host hub on this device**. Keep Token Monitor running; quitting it stops the Hub.
5. Copy the Tailscale URL and generated shared secret.
6. In Android Token Monitor, enter the address that starts with `100.`, paste the secret, and select **Connect**. A bare address is accepted; the app adds `http://` and the default port.

While on the home network, **Find** beside the home Wi-Fi field searches the current private network for a Hub and fills the address in. The search reads only the Hub's unauthenticated identity endpoint, stays within private address ranges, and runs only when tapped.

For several computers, host one Hub and set every other desktop Token Monitor to
**Connect to a hub** with that Hub's URL and secret. The Android app connects
once and receives the aggregate plus the per-device breakdown.

Tailscale may stay enabled at home. When both devices are on the same network it normally uses a direct local path. The Android app stops its Hub stream when backgrounded; an explicit widget Live session uses a lighter 30-second stats refresh instead.


Settings has an optional **Home Wi-Fi URL** beside the Hub URL. Enter the desktop's private `10.x`, `172.16–31.x`, `192.168.x`, or `.local` address there and the phone switches on its own: it tries the address that answered last, and if that one is silent it tries the other. A Tailscale address is skipped while Tailscale is off, and the home address is skipped on mobile data unless a VPN such as a Tailscale subnet router carries it. Arriving home with Tailscale off, or leaving on mobile data, needs no manual change. While the stream is up, the header names the route that answered: `Tailscale`, `Home Wi-Fi`, or `Private network`. Settings → Hub status shows the same label.

The main **Hub URL** uses Tailscale by default. **Use Wi-Fi as the main Hub address** permits a private address as the only Hub. The separate **Allow an internet Hub over HTTPS** switch permits a public DNS name over HTTPS, as described below. The optional fallback is a separate choice. Tailscale remains preferred because it supplies an encrypted identity-aware path both at home and away.

### Updating a home address

If the desktop's home address changes, open Settings, use **Find** or enter the
new **Home Wi-Fi URL**, then choose **Test and save home address**. The app checks
that address with the stored pairing secret before saving it. Your main address
and other connection settings are preserved; unrelated form edits are not saved.
A failed check keeps the previous pairing. Replacing the secret or main address
still uses **Connect** with the current Hub secret.

### Android 17 local-network access

Android 17 asks for local-network permission when you choose a home Wi-Fi
connection or **Find**. Denial leaves the saved pairing intact; a configured
Tailscale route remains available. Use **Allow home Wi-Fi access** to retry, or
the Android app-settings link if the system no longer shows a prompt. Returning
to the app rechecks permission, including changes made in Android Settings.
Revoking access excludes home routes until permission is restored.

The home Wi-Fi field remains private-only. Public IP addresses are rejected.
The optional internet route below uses the main Hub field and its own consent.

## Optional: internet Hub over HTTPS

Available in Android v0.78.0 r2. Tailscale remains the recommended setup.

1. Set up a Hub separately: the desktop project's official Cloudflare Worker
   Hub, or a Node Hub behind an HTTPS reverse proxy with a valid certificate.
2. In Android connection settings, turn on **Allow an internet Hub over HTTPS**.
   It starts off, including after an upgrade with an existing pairing.
3. Enter a public DNS name such as `hub.example.com`, or
   `https://hub.example.com:8443`, in **Hub address**. A bare name gains HTTPS;
   the default port is 443. Enter the Hub secret and choose **Connect**.

From Android v0.78.0 r3, turning the switch off asks for confirmation when the
saved main address is an internet Hub. Cancel, Back or a tap outside keeps access enabled. Choosing
**Turn off** revokes saved permission immediately. A saved Tailscale or home
Wi-Fi main address turns it off without a dialog. To enable
it again, check and save the connection with the Hub secret.

The switch only permits a connection. It does not expose a computer, open a port
or deploy anything. With a Cloudflare Worker, desktops upload to the Worker using
outbound connections; they need no incoming public port. A Node Hub behind a public
reverse proxy is deliberately internet-accessible. Never publish plain HTTP.

Only public DNS names are accepted for this option. IP literals, single-label
names, localhost, private-use suffixes, credentials in URLs, paths, queries and
fragments are refused. The existing private home fallback works as before.
Header and Hub status identify the route as **Internet (HTTPS)**.

The Hub secret can also change data on the Hub. Use a long random secret. If the
phone is lost, change the secret on the Hub and on every connected desktop.
Android only reads the Hub; the secret itself has broader authority.

Android must trust the server certificate through its system certificate store.
User-installed CAs are not accepted, and the app does not pin a Hub certificate.
Fix certificate, hostname or TLS errors on the Hub rather than bypassing them.
Redirects are refused. The unauthenticated identity check runs before protected
reads. [Security details](SECURITY.md).

## Manual pairing

Desktop Token Monitor's Hub settings list the addresses and shared secret.
Copy those into Android for the first connection. Treat the secret as a password;
do not post it in screenshots or support requests.

## Troubleshooting

- **The address labeled "Tailscale" starts with `169.254`:** the desktop address list can pick up a Windows placeholder address on an adapter with no real address. A Tailscale address always starts with `100.`; use the one shown in the Tailscale app or by `ipconfig`.
- **Connection refused:** Token Monitor desktop is not running, the Hub is not in host mode, or a local firewall is blocking it.
- **Unauthorized:** replace the saved Android connection with the current desktop Hub secret.
- **Works at home but not away:** confirm both devices appear online in Tailscale and use the listed Tailscale address as the Hub URL, keeping the home-only LAN address in the Home Wi-Fi field.
- **Old values:** check the timestamp shown in Android Settings. The app keeps the last snapshot visible when the Hub is unreachable.
