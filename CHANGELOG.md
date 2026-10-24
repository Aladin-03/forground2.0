# Changelog

All notable changes to Foreground 2.0. Versions follow `versionName` in `app/build.gradle.kts`.

## [1.0.3] — 2026-10-24

### Added

- **Usage pill.** During the 10-minute window, a pill at the top right shows the time left and today's use of the app,
  e.g. `7:42 · 1h 12m`, updating every second. It stays in the same place in portrait, landscape and fullscreen video,
  and taps go through it to the app.

## [1.0.2] — 2026-10-24

### Added

- **Lock from Home.** Tap a watched app on the Home screen to lock it in the same picker as the overlay. A locked app
  shows its time left and unlock time instead.

### Changed

- A lock started from Home takes effect immediately and overrides an active 10-minute window: the next open shows the
  lock screen.

## [1.0.1] — 2026-10-16

### Fixed

- The release build crashed on launch. R8 renamed an enum that type-safe navigation looks up by name.

## 1.0.0 — 2026-10-16

First release.

- Breath gate: a 60-second breathing pause each time a watched app is opened, then *Block this app?*
- **No** gives a 10-minute window, after which the breath returns. **Yes** opens a duration picker (days, hours,
  minutes; up to 30 days); a lock can't be cancelled or shortened.
- **Lock it now** on the breath screen, with **Back to breath** that keeps the breath's remaining time.
- Lock screen with a live countdown and a **Go home** button. Locks are per app and survive reboots.
- Media is paused and muted while an overlay shows, and never resumed.
- Watched-apps picker with search; Home with locks, permission status and one-tap fixes.
- Onboarding for Usage access, overlay, notifications and battery, plus autostart guidance for Infinix XOS.
- Stats: unlocks and screen time with weekly history, a stacked chart of the week's top apps, per-day app lists and
  an hourly unlocks chart. Each finished day is saved on the device.
- Unlocks and Screen time home-screen widgets.
- Detection survives reboots and app updates, and alerts when a required permission is revoked.
- Release signing from `keystore.properties`; the APK is named `foreground2-<version>.apk`.

[1.0.3]: https://github.com/Aladin-03/forground2.0/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/Aladin-03/forground2.0/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/Aladin-03/forground2.0/releases/tag/v1.0.1
