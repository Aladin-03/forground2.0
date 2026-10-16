# Foreground 2.0 — Spec

Personal Android app (sideloaded, single user). Every time the user enters a **watched app**, they get a 1-minute breathing pause, then choose: use it for up to 10 minutes, or lock it for a chosen duration.

**Hard constraint: NO AccessibilityService, ever.** Indian banking apps (Axis, Canara) refuse to run when an untrusted accessibility service is enabled. Detection uses Usage Access; blocking uses an overlay window.

## Behaviour rules

1. **Watched apps** are picked from a list of all launcher apps on the phone. Unwatched apps are never touched.
2. **Entry = breath.** A watched app becoming the front app after any other app (including switching back from WhatsApp) is an *entry*. Each entry shows a full-screen breathing overlay for 60 s. No buttons are interactive until it finishes. Back key does nothing.
3. **Escaping doesn't skip.** If the user presses Home mid-breath, the overlay is removed. The next entry starts a fresh full minute.
4. **Prompt.** After the breath: "Block this app?" with Yes / No.
5. **No = allowed window.** The overlay goes away and the app is usable until `now + 10 min`. If the app is still in front when the window ends, the breath overlay appears on the spot, then the prompt again. Leaving the app ends the sitting; the next entry gets a breath (rule 2).
6. **Yes = duration picker.** Scroll wheels for days (0–30), hours (0–23), minutes (0–59), plus Start. Start is disabled at zero. Once started, a lock cannot be cancelled or shortened.
7. **Locked.** Entering a locked app skips the breath and shows "Unlocks in Xd Yh Zm" (live countdown) with a single **Go home** button.
8. **Independent locks.** Each app has its own lock. When it expires, the app goes back to rule 2.
9. **Persistence.** A lock is stored as its end time (epoch ms). Locks survive reboots and process death. The allowed window is in memory only; losing it just means a fresh breath.

## Daily stats

A Stats screen, opened from Home, shows today's unlocks, total screen time and per-app foreground time, most used first. "Today" starts at local midnight. Computed from the same usage log as detection (`queryEvents` since midnight): an unlock is a `KEYGUARD_HIDDEN` event; app time comes from `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` pairs, clipped at midnight, with a still-open session counted up to now. The default launcher, system UI and this app get no time. 
### Widgets

Two Glance home-screen widgets in the app's colours (light and dark), both opening the Stats screen when tapped:
**Unlocks** ("Unlocks today" + count) and **Screen time** ("Screen time today" + total; at medium size, also the top three apps with times).

Refreshes come from the detection service: on every unlock, then every 5 minutes while unlocked, plus one just after local midnight so both reset on time. `updatePeriodMillis` (30 min) is only a fallback. Nothing refreshes while the screen is off, and each widget refreshes at most once a minute (a throttled refresh is deferred until allowed; the first refresh of a new day always goes through).

## Out of scope

Accessibility service · clock-tamper protection · broken-lock logging · special handling for browsers · any task/credits/planner features.

## Technical approach

- **Detection:** a foreground service (type `specialUse`, persistent notification) polls `UsageStatsManager.queryEvents` every ~500 ms and tracks `ACTIVITY_RESUMED` events to know the current front package. A short flash of the app before the overlay covers it is acceptable.
- **Overlay:** a `WindowManager` window of type `TYPE_APPLICATION_OVERLAY`, full screen, focusable, consuming touches and the back key, hosting a `ComposeView`. The service must provide the `ComposeView` with a `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` by hand.
- **Media:** while any overlay shows, media is paused with media keys and the media stream is muted (some players, like YouTube Shorts, have no media session and can't be paused from outside). On hide the stream is unmuted, but playback is never resumed. A mute the user made is left alone, and a mute left by a run that died mid-overlay is undone on the next service start.
- **App list:** `PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` with a matching `<queries>` manifest entry. No `QUERY_ALL_PACKAGES`.
- **Storage:** Room for watched apps and locks; DataStore Preferences for flags (e.g. onboarding done).
- **Reliability:** the service is `START_STICKY` and restarted by a `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver. Every start rereads locks from Room and starts gates fresh. While running it rechecks the required permissions every few seconds and posts an alert notification if Usage access or the overlay permission is revoked (cleared once regranted). The home screen shows locks, permission and service status, with one button to fix the most urgent problem.

### Permissions

| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Know the front app |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draw breath / prompt / lock overlays |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep detection running |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot |
| Battery-optimisation exemption + OEM autostart | Infinix XOS kills background services aggressively |

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + pure domain layer · Hilt · Room · DataStore Preferences · KSP (not kapt) · detekt + ktlint · minSdk 29, target/compile SDK 35 · single `app` module.

Test device: physical Infinix X6871. Built and run from VS Code + terminal (`./gradlew`, `adb`), not Android Studio.
