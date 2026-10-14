# Token Monitor for Android v0.70.0 r1

This release follows desktop Token Monitor v0.70.0. It adds the desktop's new
token number animation, names the new CatPaw tool and shows MiniMax
pay-as-you-go balances in Limits.

## What changed

- Token totals use the desktop's digit roll: each changed digit rolls in while
  unchanged leading digits stay put. It applies to the Home total and to
  breakdown rows. **Settings → Appearance → Token number animation** lets you
  choose **Digit roll**, the default, or **Count up**, the previous behavior.
  Reduce motion still turns animation off. The roll leaves out the desktop's
  brief blur.
- CatPaw sessions and usage appear under the desktop's CatPaw name. CatPaw
  doesn't report prices, so its usage shows as unpriced tokens.
- A MiniMax key in pay-as-you-go mode shows its CNY or USD balance in Limits
  with a symbol and two decimals, as on the desktop, and a full bar while
  money remains. A zero or negative balance appears as reported. The card
  names the plan, Token Plan or Pay-as-you-go, and Token Plan keys keep their
  five-hour and weekly windows.
- Balances and spend show a `¥` or `$` symbol and two decimals, as the desktop
  does, so a MiMo wallet reads `¥42.00` rather than `CNY 42`.
- Large session lists skip grouping work when no session links to another,
  matching the desktop's faster session lists.

Collecting CatPaw usage, choosing a MiniMax billing mode and reading Codex
compressed logs stay on the desktop, and the app still only reads. The
fixed-aspect Pages widget, widget coordinates, Wi-Fi fallback, bounded Live
sessions and release signing identity are unchanged.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.70.0, with earlier Hub
fixtures retained. An older Hub keeps its existing display. Package
`io.github.theminionooo.tokenmonitor`, version code `700001`, signed with the
existing release certificate. Use **Settings → App updates** or install the APK
over your current release. Do not uninstall first. See
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

Verification includes JVM tests, lint, emulator checks and the released Hub
contract. Build and emulator evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
This release changes only how data is presented, so I haven't tested it on my
phone.
