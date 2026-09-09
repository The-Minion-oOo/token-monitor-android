# Changelog

Android releases use the desktop protocol version plus an Android revision. Only
published revisions appear below; unreleased candidate work is folded into the
release that first shipped it.

## Unreleased

- Nothing yet.

## v0.54.0 — September 9, 2026

### r21

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
