<p align="center">
  <img src="docs/images/app-icon.png" alt="Token Monitor app icon" width="96" height="96">
</p>

<h1 align="center">Token Monitor for Android</h1>

<p align="center"><b>Your desktop usage. In your pocket.</b><br>
Tokens, account limits, models and trends from the desktop Token Monitor Hub, plus home-screen widgets that stay useful with the app closed.</p>

<p align="center">
  <a href="docs/INSTALL.md">Build or install</a> ·
  <a href="docs/PAIRING.md">Pair with your desktop</a> ·
  <a href="docs/WIDGETS.md">Widgets</a> ·
  <a href="CHANGELOG.md">What changed</a>
</p>

<p align="center">
  <a href="https://github.com/The-Minion-oOo/token-monitor-android/actions/workflows/android.yml"><img alt="Android checks" src="https://img.shields.io/badge/Android_checks-CI-73bdf5?style=flat-square&logo=githubactions&logoColor=10221c"></a>
  <img alt="Android 8 or newer" src="https://img.shields.io/badge/Android-8%2B-6fa79b?style=flat-square&logo=android&logoColor=10221c">
  <img alt="Desktop baseline v0.60.0" src="https://img.shields.io/badge/Desktop-v0.60.0-73bdf5?style=flat-square">
  <img alt="Kotlin and Compose" src="https://img.shields.io/badge/Kotlin-Compose-ff9c72?style=flat-square&logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-b39cff?style=flat-square"></a>
</p>

<img src="docs/images/hero.png" alt="The Android dashboard beside the detailed and compact widgets, all showing made-up usage data" width="100%">

Your desktop [Token Monitor](https://github.com/Javis603/token-monitor) already
tracks the usage. This app brings the same dashboard, visual language, and
numbers to your phone, with home-screen widgets for quick checks.

The desktop stays the collector and the source of truth. The phone reads its Hub over your own private network and shows what it finds. That is the whole trick.

## The desktop dashboard, made mobile

<table>
  <tr>
    <td align="center" valign="top" width="33%"><a href="docs/images/home.png"><img src="docs/images/framed-home.png" alt="Home dashboard with totals, limits, tools, devices and models" width="100%"></a><br><sub><b>Command center</b><br>Totals, limits, tools, devices, models</sub></td>
    <td align="center" valign="top" width="33%"><a href="docs/images/filtered-models.png"><img src="docs/images/framed-filtered-models.png" alt="Models filtered to Codex after tapping it in the tools list" width="100%"></a><br><sub><b>Tap a tool</b><br>See the models behind it</sub></td>
    <td align="center" valign="top" width="33%"><a href="docs/images/trends.png"><img src="docs/images/framed-trends.png" alt="Usage overview cards and an activity heatmap with a selected day" width="100%"></a><br><sub><b>Find the busy days</b><br>Cards, heatmap, daily series</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%"><a href="docs/images/devices.png"><img src="docs/images/framed-devices.png" alt="Usage grouped by two made-up desktop devices" width="100%"></a><br><sub><b>Every desktop</b><br>What each machine contributes</sub></td>
    <td align="center" valign="top" width="33%"><a href="docs/images/projects.png"><img src="docs/images/framed-projects.png" alt="Searchable project usage with token and cost totals" width="100%"></a><br><sub><b>Projects and sessions</b><br>Searchable, without transcripts</sub></td>
    <td align="center" valign="top" width="33%"><a href="docs/images/settings.png"><img src="docs/images/framed-settings.png" alt="Appearance, dashboard, widget and connection settings" width="100%"></a><br><sub><b>Make it yours</b><br>Themes, text size, motion, views</sub></td>
  </tr>
</table>

> Every image is a capture of the Android app using made-up accounts, devices,
> projects, and usage. Capture instructions are in the [development guide](docs/DEVELOPMENT.md#showcase-captures).

## Widgets that earn their space

<img src="docs/images/widget-gallery.png" alt="The five widget layouts: compact, wide, portrait, overview and detailed" width="100%">

The original Usage widget is the dashboard shrunk to a card, not a different app that happens to share a name. Same type, same palette, same `Reset 1d 13h` countdowns. It starts at 2×2 and reflows as you resize it, from a count you can read across the room to a detailed panel with limits and a seven-day chart.

The separate Pages widget is deliberately fixed-layout. Its left and right edge controls move through Overview, Limits, Breakdown, and Activity inside the same 1.82:1 card; it does not reflow into alternate compositions. The launcher always receives one complete page, so One UI cannot shrink the pages into its collection fan. Bars and charts are drawn at the exact density of the allocated card, so they stay sharp instead of being stretched.

### Four views. One fixed footprint.

<a href="docs/images/widget-pages-gallery.png"><img src="docs/images/widget-pages-gallery.png" alt="The Pages widget showing Overview, Limits, Breakdown and Activity at one consistent size" width="100%"></a>

Overview keeps the headline total readable. Limits gives each provider room for
its tightest windows. Breakdown aligns tools and models on one row rhythm, and
Activity combines the seven-day chart with recent history. Every image above is
rendered by the production widget from the dense showcase fixture.

Tap either edge to move between pages. The card stays at the same 1.82:1 shape
and uses one composition at every launcher allocation; only the whole card is fitted.
The type scale stays consistent, and the raised page dots keep your place. Moving
between pages is entirely local: it does not wake the Hub, start Live, or schedule
background work.

- **Full numbers.** Every digit, comma-separated, rolling when the total changes. Never "3.5M".
- **Refresh once.** A compact refresh icon in a transparent 48 dp touch target fetches a fresh snapshot without opening the app.
- **Live for an hour.** A toggle in the header checks the Hub every 30 seconds while you do other things; the knob glows while it is connected. Tap it again, or Stop in the notification, to end early.
- **More than the count.** Messages, active time and streak beside the figure, today's split by tool, every limit window with its reset, and the week total on the chart, as space allows.
- **Saved when offline.** The last snapshot stays on the home screen with its time, so `SAVED` never pretends to be live.

<img src="docs/images/widget-themes.png" alt="Widgets in the Default, Obsidian, Porcelain and a custom theme" width="100%">

Pick a theme in Settings, or paste a `TM1-…` code from the desktop app, and the widgets follow on the spot. [Layouts, sizes and Live behavior](docs/WIDGETS.md) · [Design notes](docs/WIDGET_DESIGN.md).

## How it works

```mermaid
flowchart LR
    D[Desktop Token Monitor<br>collects and hosts usage] -->|read-only Hub| T[Tailscale or home Wi-Fi<br>your private network]
    T --> A[Android app<br>dashboard and widgets]
```

The phone talks to the Hub the desktop already runs. It reads five documented
endpoints and one live stream over Tailscale, or home Wi-Fi when enabled. There
is no public server, vendor relay, or separate Token Monitor account.

## What it shows

- Live totals while the app is open, an immediate refresh on return, pull-to-refresh, and a saved snapshot when the Hub is unavailable.
- Day, week, month, rolling 7, 30 and 90 days, one year, all history and total.
- Account limits with the desktop's reset countdowns.
- Tools, devices, models, projects, sessions, subscriptions, service status, activity and trends, each with an `updated 5m ago` freshness.
- Session activity and context-window use when desktop v0.60.0 reports them, without reading prompt or response text.
- Trends by tool or model, shown as bars or a K-line chart.
- Cache hit, cache miss, output and unclassified token details where the Hub provides them.
- The desktop's Default, Obsidian and Porcelain themes, theme codes pasted as-is, an option to follow the phone's light and dark setting, three text sizes, motion controls, and reorderable views and Home modules.
- A Back button that goes Home instead of quitting on you, and a light haptic tick on every tab.

The Hub deliberately does not carry prompt or response text, so the phone never sees it. Your conversations stay on your desk.

## Private and light

- Read-only. The app cannot change desktop settings, usage data, or files.
- Hub credentials live in an Android Keystore-backed store and are excluded from backups.
- No ads, analytics, wake lock, scheduled background work or "please rate us" popup.
- The visible dashboard streams immediately. Widget Live uses a lightweight 30-second stats refresh and stops after one hour.
- Android 13 and newer asks for notification permission the first time you start a widget session, so the Stop control has somewhere to live. Ordinary use needs no permission prompts at all.

[Privacy and security](docs/SECURITY.md) · [Report a concern](SECURITY.md).

## Get connected

You need Android 8.0 or newer and a desktop running Token Monitor v0.60.0 with Hub hosting on.

1. Follow [Installing and updating](docs/INSTALL.md) to get the signed v0.60.0 r7 APK.
2. Put [Tailscale](https://tailscale.com/) on the desktop and the phone, signed into the same tailnet.
3. In desktop Token Monitor, open **Settings → Multi-device Sync → Host Hub** and copy the address and shared secret.
4. In the app, type the address that starts with `100.`, paste the secret, tap **Connect**. Just the numbers are enough.
5. At home, tap **Find** and the app fills in the desktop's Wi-Fi address as a fallback. From then on the phone uses whichever route answers.

[Pairing and troubleshooting](docs/PAIRING.md) · [Installing and updating](docs/INSTALL.md).

## Compatibility

| | |
| --- | --- |
| Current Android release | [`v0.60.0-r7`](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.60.0-r7) |
| Current source version | `v0.60.0-r7` ([release notes](docs/releases/android-v0.60.0-r7.md)) |
| Latest phone-verified build | `v0.60.0-r7` ([release notes](docs/releases/android-v0.60.0-r7.md)) |
| Verified desktop baseline | Token Monitor `v0.60.0` |
| Upstream commit | [`8031cf3`](https://github.com/Javis603/token-monitor/commit/8031cf3b75c7f354db8a990a983999c69086da28) |

The visible version matches the desktop release the phone understands. Android-only
builds bump the release revision and internal version code while the compatibility
line stays on its verified desktop version. When desktop Token Monitor moves past that, the phone
continues using the verified endpoints until a follow-up Android release checks
the new protocol. Details are in [`upstream.json`](upstream.json) and
[`RELEASING.md`](docs/RELEASING.md).

## Build and contribute

JDK 17 or newer, Android SDK 37 and the bundled Gradle wrapper:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Add `-PtokenMonitorPreview=true` to install a separate `.preview` build next to the release without touching its pairing. [Development guide](docs/DEVELOPMENT.md) · [Contributing](CONTRIBUTING.md).

## Documentation

| Using it | Understanding it | Maintaining it |
| --- | --- | --- |
| [Installation](docs/INSTALL.md) | [Architecture](docs/ARCHITECTURE.md) | [Development](docs/DEVELOPMENT.md) |
| [Pairing](docs/PAIRING.md) | [Hub protocol](docs/PROTOCOL.md) | [Validation](docs/VALIDATION.md) |
| [Widgets](docs/WIDGETS.md) | [Desktop parity](docs/DESKTOP_PARITY.md) | [Releasing](docs/RELEASING.md) |
| [Privacy](docs/SECURITY.md) | [Widget design](docs/WIDGET_DESIGN.md) | [Following upstream](docs/UPSTREAM_SYNC.md) |

This project is independent of the upstream Token Monitor maintainers and of the services whose marks appear in the app. [MIT License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md).

<p align="center">
  Made with ❤️ by <a href="https://github.com/The-Minion-oOo"><b>The_Minion_oOo</b></a><br>
  <sub>...: Thanks to Codex and Claude :...</sub>
</p>
