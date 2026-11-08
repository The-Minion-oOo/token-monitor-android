# Android v0.88.0 r2

I fixed the missing Live status chip on supported Samsung phones.

## What's new

- **Live at a glance.** On an unlocked Samsung phone running Android 17 or newer,
  Live can show a status-bar chip with the current session count or usage.
- **Clear app identity.** The expanded card keeps Token Monitor's name and icon,
  the Live end time and Stop action.
- **A reliable lock-screen notification.** Locking the phone switches to the
  ordinary notification with the full reading and Stop. Other phones and blocked
  promotion use the ordinary notification too.

The chip follows Android's notification and promotion settings. Samsung's card
integration depends on phone software and may change in a future system update.

## Updating

Use **Settings → App updates**, or install the signed APK over the current copy.
Android 8 or newer is required.
[Install and update](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).

I installed this APK over r1 on my phone. Saved pairing, visible settings and the
widget survived. I checked fresh Home Wi-Fi data, the unlocked chip, app branding
and Stop from both the chip and ordinary lock-screen notification.
Battery use and automatic one-hour expiry were not measured.
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
