# Pairing with a desktop Hub

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


Settings has an optional **Home Wi-Fi URL** beside the Hub URL. Enter the desktop's private `10.x`, `172.16–31.x`, `192.168.x`, or `.local` address there and the phone switches on its own: it tries the address that answered last, and if that one is silent it tries the other. Arriving home with Tailscale off, or leaving on mobile data, needs no manual change. While the stream is up, the header names the route that answered: `Tailscale`, `Home Wi-Fi`, or `Private network`. Settings → Hub status shows the same label.

The main **Hub URL** must be a Tailscale address unless **Allow a private Wi-Fi Hub** is enabled, which lets an isolated home network use a private address as the only Hub. Tailscale remains preferred because it supplies an encrypted identity-aware path both at home and away.

Public IP addresses and ordinary public hostnames are rejected for both fields. Do not port-forward the Hub.

## No QR code in the current desktop release

Token Monitor v0.56.0 lists Hub URLs and the secret but does not generate a pairing QR code. Manual copy is therefore the supported first pairing flow. A future QR flow should be added only when both apps can keep the payload local and clearly warn that the code contains the Hub secret.

## Troubleshooting

- **The address labeled "Tailscale" starts with `169.254`:** the desktop address list can pick up a Windows placeholder address on an adapter with no real address. A Tailscale address always starts with `100.`; use the one shown in the Tailscale app or by `ipconfig`.
- **Connection refused:** Token Monitor desktop is not running, the Hub is not in host mode, or a local firewall is blocking it.
- **Unauthorized:** replace the saved Android connection with the current desktop Hub secret.
- **Works at home but not away:** confirm both devices appear online in Tailscale and use the listed Tailscale address as the Hub URL, keeping the home-only LAN address in the Home Wi-Fi field.
- **Old values:** check the timestamp shown in Android Settings. The app keeps the last snapshot visible when the Hub is unreachable.
