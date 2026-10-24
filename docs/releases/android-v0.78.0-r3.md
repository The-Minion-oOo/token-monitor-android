# Android v0.78.0 r3

I added confirmation before turning off internet access for a saved internet Hub.
An accidental tap no longer revokes the pairing immediately. Tailscale remains
recommended, and internet access stays off by default.

## What changed

- Turning off **Allow an internet Hub over HTTPS** for a saved internet main
  address asks for confirmation. **Cancel** is the default action.
- Cancel, Back or a tap outside keeps the switch on and leaves pairing unchanged.
  **Turn off** uses the existing revocation path.
- Initial pairing and saved Tailscale/home Wi-Fi main addresses keep the plain
  toggle, without a confirmation dialog.

I kept the existing dialog styling and theme colours. The Hub protocol,
networking protections and widgets are unchanged.
[Pairing](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/PAIRING.md).

## Testing

This small UI change was not phone-tested. I checked the dialog on the emulator
in light and dark themes, including cancellation, Back, outside taps and confirmed
revocation. Unit tests, lint, debug assembly and CI must pass before publication.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
