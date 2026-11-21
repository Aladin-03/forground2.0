# Foreground 2.0 — Spec

Personal Android app (sideloaded, single user). Every time the user enters a **watched app**, they get a 1-minute breathing pause, then choose: use it for a while (10, 5 or 1 minute, depending on the app's **watch mode**), or lock it for a chosen duration.

**Hard constraint: NO AccessibilityService, ever.** Indian banking apps (Axis, Canara) refuse to run when an untrusted accessibility service is enabled. Detection uses Usage Access; blocking uses an overlay window.

## Behaviour rules

1. **Watched apps** are picked from a list of all launcher apps on the phone, each in a **watch mode** (see "Watch modes" below). Unwatched apps are never touched.
2. **Entry = breath.** A watched app becoming the front app after any other app (including switching back from WhatsApp) is an *entry*. Each entry shows a full-screen breathing overlay for 60 s. Back key does nothing. Its only interactive element is a **Lock it now** button at the bottom, under the line "Save your time, save your future."; it opens the duration picker straight away (rule 6). There is no way to say No or skip the breath.
3. **Escaping doesn't skip.** If the user presses Home mid-breath, the overlay is removed. The next entry starts a fresh full minute.
4. **Prompt.** After the breath: "Block this app?" with Yes / No.
5. **No = allowed window.** The overlay goes away and the app is usable until `now + window`, where the window is the app's watch mode's at the moment No is tapped (Mindful 10 min, Focused 5 min, Strict 1 min). If the app is still in front when the window ends, the breath overlay appears on the spot, then the prompt again. Leaving the app ends the sitting; the next entry gets a breath (rule 2).
6. **Yes = duration picker.** Scroll wheels for days (0–30), hours (0–23), minutes (0–59), plus Start. Start is disabled at zero. Once started, a lock cannot be cancelled or shortened. The picker opened by **Lock it now** (rule 2) starts locks exactly like the Yes path, and also has **Back to breath**, which returns to the breath with the time it had left when Lock it now was tapped: the breath's countdown is paused while the picker is open, so going back can never skip or shorten it, however long the picker stayed up. Leaving the app from either picker ends the sitting as in rule 3. The Yes-path picker has no Back.
   **From Home.** Tapping a watched app on the Home screen opens the same picker full screen, under the app's icon and above Start the line "Save your time, save your future."; Start stores the lock the same way (a later existing lock is kept) and returns to Home, and Cancel or Back changes nothing. A watched app that is already locked shows its time remaining and unlock time instead, read-only. The service observes the locks table, so the lock is enforced at once and wins over an allowed window: the next entry shows the lock screen, not the breath.
7. **Locked.** Entering a locked app skips the breath and shows "Unlocks in Xd Yh Zm" (live countdown) with a single **Go home** button.
8. **Independent locks.** Each app has its own lock. When it expires, the app goes back to rule 2.
9. **Persistence.** A lock is stored as its end time (epoch ms). Locks survive reboots and process death. The allowed window is in memory only; losing it just means a fresh breath.
10. **Usage pill.** While a watched app is in its allowed window (rule 5, so it counts down that app's own window), a small pill reads "<m:ss> · <usage>", e.g. "7:42 · 1h 12m": the time left until the next breath, and the app's foreground time today ("1h 12m", or "12m" under an hour), both ticking every second. It is meant to catch the eye as a reminder: a solid dark chip with larger text, a light outline and a shadow, at the top right just below the status bar (the gap beside the camera is too small for it), at the same spot in portrait, landscape and fullscreen video. It is always visible (no fading, no colour change near the end) and lets every tap through to the app. It is hidden whenever the app leaves the front and whenever the breath, prompt, picker or lock screen shows; unwatched apps never get one.

## Watch modes

Each watched app has a mode that sets its allowed window (rule 5). The breath is 60 s in every mode.

| Mode | Shown as | Window |
|---|---|---|
| Mindful | Mindful · 10 min | 10 min |
| Focused | Focused · 5 min | 5 min |
| Strict | Strict · 1 min | 1 min |

The order Mindful → Focused → Strict is loose → strict. **Tightening** means moving towards Strict; **loosening** means moving towards Mindful. Unwatching counts as loosening all the way.

1. **Enabling.** Switching an app on in the picker opens the mode chooser (the three modes with their windows). Picking one watches the app in that mode. Dismissing it or pressing Back leaves the app unwatched: there is no watched-without-a-mode state.
2. **Changing mode.** On Home, each watched app shows a mode chip (e.g. "Strict · 1 min"). Tapping the chip opens the chooser with the current mode marked; tapping the row itself still opens the lock picker (rule 6).
3. **Tightening is instant.** It is stored at once.
4. **Loosening and unwatching are slow.** A looser mode, or switching the app off in the picker, first goes through the **sit-through countdown**; only when it completes is the change stored.
5. **Locked apps.** While an app has an active lock it can't be loosened or unwatched: its picker switch and the looser options in the chooser are disabled and show "Locked · <time left>". Tightening is still allowed. They come back by themselves when the lock ends.
6. **From the next window.** A mode change (either way) never touches a running window or breath: the window that is running keeps its end, and the next No uses the new mode. The service observes the watched-apps table, so this needs no restart.

**Sit-through countdown.** A full-screen screen in the app: what will change ("Stop watching YouTube" or "Loosen YouTube to Mindful"), a 5:00 countdown, the line "Take this time to think it over." and a Cancel button. Nothing shortens it. The screen stays on while it runs. Leaving the screen any way (Back, Cancel, Home, switching apps, screen off, process death) cancels it and changes nothing; the next attempt starts again from 5:00. A rotation is not leaving. Until it completes, the app stays guarded in its current mode. When it completes the change is stored (unless the app was locked or its mode changed meanwhile) and the user is back where they came from (the picker or Home).

**Storage.** The mode is a column of `watched_apps` (database v5). Apps watched before modes existed became Mindful, their window until then; the migration only adds the column, so watched apps, locks and usage history are kept.

## Daily stats

A Stats screen, opened from Home or a widget, shows unlocks and screen time. "A day" runs from local midnight to the next one, in the device's current time zone. Stats are computed from the same usage log as detection (`queryEvents`): an unlock is a `KEYGUARD_HIDDEN` event, also counted per local hour. App time comes from `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` pairs, clipped at midnight, with a still-open session counted up to now. The default launcher, system UI and this app get no time.

**History.** Android keeps its event log only for a limited time, so each finished day is saved to Room (`daily_stats`, `daily_app_usage`, upserted by date). Days are saved:
- by the detection service on its first poll after local midnight, which rewrites yesterday;
- by a daily WorkManager job as a fallback, which also rewrites yesterday;
- on app start, service start and opening Stats, which backfill every missing day in the last 30 that is still fully in the event log.

All of these are safe to repeat. Today is always live from the log; past days come from Room.

**Screen.** There are two tabs, Unlocks and Screen time, over a pager of Monday–Sunday weeks. The pager opens on the current week, and swiping right goes back to the earliest week with data.
- Each week shows its date range and daily average, with an empty state if it has no data.
- **Screen time:** one stacked column per day, split into the week's top 5 apps plus a grey "Other". An app's colour comes from a fixed palette and stays the same on every week. A legend under the chart lists the colours.
- **Unlocks:** one column per day with the count above it.
- Today's column is highlighted. Tapping a column selects that day: Screen time then shows the day's total and full app list, and Unlocks shows the day's total and 24 hourly bars. The default selection is today on the current week and Sunday on past weeks.

### Widgets

Two Glance home-screen widgets in the app's colours (light and dark), both opening the Stats screen (matching tab, current week, today) when tapped:
**Unlocks** ("Unlocks today" + count) and **Screen time** ("Screen time today" + total; at medium size, also the top three apps with times).

Refreshes come from the detection service: on every unlock, then every 5 minutes while unlocked, plus one just after local midnight so both reset on time. `updatePeriodMillis` (30 min) is only a fallback. Nothing refreshes while the screen is off, and each widget refreshes at most once a minute (a throttled refresh is deferred until allowed; the first refresh of a new day always goes through).

## Out of scope

Accessibility service · clock-tamper protection · broken-lock logging · special handling for browsers · any task/credits/planner features.

## Technical approach

- **Detection:** a foreground service (type `specialUse`, persistent notification) polls `UsageStatsManager.queryEvents` every ~500 ms and tracks `ACTIVITY_RESUMED` events to know the current front package. A short flash of the app before the overlay covers it is acceptable.
- **Overlay:** a `WindowManager` window of type `TYPE_APPLICATION_OVERLAY`, full screen, focusable, consuming touches and the back key, hosting a `ComposeView`. The service must provide the `ComposeView` with a `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` by hand.
- **Usage pill:** its own `TYPE_APPLICATION_OVERLAY` window, separate from the full-screen one: `WRAP_CONTENT`, gravity top-end, not focusable and not touchable, window alpha 0.8 (Android 12+ blocks touches through untrusted overlays more opaque than that). Today's use is read once from the usage log when the pill appears, then counted up locally. Its offsets are constants in `core/config`.
- **Media:** while any overlay shows, media is paused with media keys and the media stream is muted (some players, like YouTube Shorts, have no media session and can't be paused from outside). On hide the stream is unmuted, but playback is never resumed. A mute the user made is left alone, and a mute left by a run that died mid-overlay is undone on the next service start.
- **App list:** `PackageManager.queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` with a matching `<queries>` manifest entry. No `QUERY_ALL_PACKAGES`.
- **Storage:** Room for watched apps (with their mode) and locks; DataStore Preferences for flags (e.g. onboarding done). Every schema change ships with a Room `Migration` and a migration test; there is no destructive fallback.
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
