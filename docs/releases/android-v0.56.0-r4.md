# Android v0.56.0 r4

This release introduces a Pages widget for people who want more than one usage
summary without giving several home-screen slots to Token Monitor. Overview,
Limits, Breakdown, and Activity now share one wide card and one saved snapshot.

## What changed

- **One steady footprint.** The launcher receives a single full-size 1.82:1 card,
  preventing Samsung One UI from shrinking and fanning the pages as a collection.
- **Four focused views.** Overview covers totals and recent activity; Limits keeps
  account windows together; Breakdown compares tools and models; Activity combines
  the seven-day chart with recent history.
- **Consistent presentation.** All four pages use the same card geometry and type
  scale. Long provider and model names truncate cleanly instead of forcing the rest
  of the page into smaller text.
- **Simple navigation.** Tap the left or right edge to change pages. Four indicators
  show the current position, and the selected page is remembered per widget.
- **Desktop v0.56.0 compatibility.** Reset and expiry wording and background-review
  session metadata now match the current desktop Hub while older v0.54.0 and
  v0.55.0 responses remain supported.

Changing pages is entirely local: it redraws the saved snapshot without starting a
network request, timer, service, or wake lock. Refresh and the optional one-hour Live
session keep their existing explicit controls.

## Install

Requires Android 8.0 or newer and desktop Token Monitor v0.56.0. Install this APK
over the existing release to preserve pairing, preferences, and the saved snapshot.
See [Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

The automated build and API 36 emulator interaction suites pass. Physical inspection
of all four pages on the Galaxy S25 Ultra remains pending and is tracked separately
in [Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
