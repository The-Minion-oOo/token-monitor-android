# Changelog

Android releases use the desktop protocol version plus an Android revision.
Unreleased work stays under **Next release** until its signed APK is published.

## Next release — v0.60.0 r6

- Give the common one-tool/one-model Breakdown state a dedicated feature
  layout with large, aligned token and share figures, explicit captions, and
  full-width bars. Dense multi-row data keeps the compact table layout.

- Verify the desktop Token Monitor v0.60.0 Hub contract while retaining the
  v0.54.0, v0.55.0, and v0.56.0 compatibility fixtures.
- Negotiate stream protocol v2 and merge lightweight freshness events into the
  last complete snapshot without dropping periods, limits, devices, or history.
- Show a session as Running, Finished, or Idle from the new tri-state turn signal,
  and show recent context-window use when both token values are available.
- Recognize the current Factory credit/allowance limit shapes and add Qwen,
  NVIDIA, and StepFun provider presentation without changing the read-only boundary.
- Rebuild the four widget pages on one measured grid written down in
  `docs/WIDGET_SPEC.md`. Every position and type size now comes from that
  specification, the card scales as one picture to any launcher size, and the
  Overview stats, Limits cells, Breakdown rows and Activity charts match the
  approved concept cards instead of colliding.
- Bundle a Latin subset of JetBrains Mono for the widget pages so Samsung
  launchers, which substitute their own monospace face, render the same card
  the emulator does. Figures keep the system sans-serif with tabular numerals.
- Group the Limits page by provider, each with its two tightest windows, and
  draw bars, the seven-day chart and the heatmap as vectors on the card instead
  of scaled bitmaps.
- Render the card at the launcher's display density instead of enlarging a
  560-pixel raster. Type, icons, borders, and charts stay sharp on high-density
  Samsung launchers while remaining inside Android's widget bitmap budget.
- Fix eleven imported vendor logos whose SVG arc flags Android's path parser
  rejects. Drawing a Qwen, Gemini, Meta, Cohere, Kimi, MiniMax, Doubao, Hunyuan,
  OpenRouter, xAI or Xiaomi mark in the widget crashed the render.
- Render the widget gallery and the widget design test from the dense showcase
  fixture rather than a two-row protocol sample, so emulator evidence shows the
  card at real density.
- Split stream delivery and session display rules into focused modules so future
  desktop upgrades remain fixture-and-adapter work instead of screen rewrites.

## v0.56.0 — September 13, 2026

### r4

- Verify protocol compatibility with desktop Token Monitor v0.56.0, including
  typed reset/expiry boundaries and background-review session metadata. Older
  v0.54.0 and v0.55.0 Hub responses remain supported.
- Add a separate medium/large pages widget with Overview, Limits, Breakdown,
  and Activity backed by one cached snapshot. The launcher receives one fixed
  1.82:1 card instead of a collection, preventing One UI from shrinking and
  fanning the pages.
- Add accessible left and right edge controls plus four page dots. Changing a
  page is local and adds no timer, network request, or background work.
- Use one shared typography scale across all four pages. Only the headline token
  total is intentionally larger; long provider and model names truncate instead
  of forcing smaller text.
- Replace the generic deck picker art with a fixed, populated preview that uses
  the same wide proportions and information hierarchy as the installed widget.
- Replace the widget refresh text glyph with a vector icon in a transparent
  48 dp touch target while retaining the existing five responsive widget sizes.
- Keep immediate streaming while the dashboard is open, and use a lighter
  30-second stats refresh during an explicit widget Live session.
- Repost the Live notification only when its visible content changes.
- Finish queued snapshot saves and cache clears before repository shutdown.

## v0.54.0 — September 8, 2026

### r20

- Use the overview widget layout at Samsung's common 4×2 height, with responsive
  tool and limit detail as space permits.

### r19

- Show current usage and the tightest provider windows in the Live notification.
- Request Android 16 notification promotion and declare lock-screen widget
  eligibility where the launcher supports it.

### r18

- Replace the widget button row with compact Refresh and Live controls.
- Add responsive message, active-time, streak, tool-share, quota-window, and
  seven-day details using data already present in the saved snapshot.

### r17

- Label the active Tailscale, home Wi-Fi, or private-network route.
- Add a setting that follows the phone's light and dark mode.
- Distinguish same-named limit windows by their period on Home, Limits, and widgets.

### r14

- Reduce the visual weight of widget controls while preserving 48 dp touch targets.
- Correct README hero and gallery alignment.

### r13

- Redesign all widget sizes with dashboard typography, sharp size-aware charts,
  responsive spacing, full counters, Refresh, and bounded one-hour Live mode.
- Add tool-filtered model pages, heatmap day details, usage search, completed-period
  comparisons, and denser dashboard lists.
- Improve stream cancellation, snapshot recovery, retained device history, and
  shared repository ownership between the dashboard and widget service.
- Split the Compose interface into focused screen and shared-component files.

## v0.54.0 — September 7, 2026

### r5

- Open activity-heatmap days to show their date, tokens, and cost.

### r4

- Add interface themes, desktop theme codes, live indicators, and tool-icon
  preferences.

### r3

- Make Settings sections expand one at a time.
