# Android v0.56.0 r4

- Adds a medium/large Pages widget with Overview, Limits, Breakdown, and Activity.
- Keeps one full-size 1.82:1 card in the launcher instead of exposing a widget
  collection, avoiding One UI's reduced and fanned collection presentation.
- Uses consistent typography across all four pages, with long names ellipsized
  instead of shrinking the surrounding text.
- Adds 48 dp left and right edge controls plus four page indicators. Page changes
  are local and do not start a request, timer, service, or wake lock.
- Updates the widget picker preview and supports the desktop v0.56.0 Hub while
  retaining v0.54.0 and v0.55.0 protocol fixtures.

Compatible with Android 8.0+ and desktop Token Monitor v0.56.0. Install over the
existing release to preserve pairing. See [Install and update](../INSTALL.md).

Automated and emulator checks pass. Installation and inspection of all four pages
on the Galaxy S25 Ultra remain pending and are recorded separately in
[Validation](../VALIDATION.md).
