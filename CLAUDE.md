# Foreground 2.0

Personal Android app: entering a watched app triggers a breathing pause, then the user either gets a time-boxed
window or locks the app. Full behaviour and technical approach: **docs/SPEC.md** (read it before changing behaviour).

Single `app` module, package `com.aladin.foreground2`. Kotlin, Compose (Material 3), Hilt, Room, DataStore
Preferences, Navigation Compose (type-safe `@Serializable` routes), KSP. minSdk 29, target/compile 35.
All versions live in `gradle/libs.versions.toml`. `detekt-rules/` is build tooling (custom detekt rules), not app code.

## Package map (`app/src/main/java/com/aladin/foreground2/`)

| Package | What belongs there |
|---|---|
| `core/designsystem/` | Theme, colour (`Palette`), spacing (`ForegroundTheme.spacing`), sizes (`ForegroundTheme.sizing`), typography tokens |
| `core/domain/` | Pure Kotlin: models, gate state machine, `Clock` |
| `core/data/db/` | Room database, entities, DAOs |
| `core/data/apps/` | Installed-app sources via PackageManager (launcher app list, icons) |
| `core/data/repo/` | Repository interfaces + impls; the rest of the app depends on these, not on DAOs |
| `core/permissions/` | Permission checks and Settings deep-link intents |
| `core/logging/` | `Log.kt`, the only logger |
| `core/config/` | `Timing.kt` (breath, window, poll interval from BuildConfig) |
| `feature/{onboarding,picker,home}/` | ViewModel + route; Composables in `ui/` |
| `service/detection/` | Foreground service + front-app detection (UsageStatsManager) |
| `service/overlay/` | Overlay window host + overlay screens |
| `di/` | Hilt modules |

Each package has a README.md with a one-line description.

## Rules

- **No AccessibilityService, ever.** Banking apps refuse to run with one enabled. Detection uses Usage Access, blocking uses an overlay.
- **No hardcoded UI strings.** Text goes in `res/values/strings.xml`, read with `stringResource()`.
  Enforced by detekt `fg2:HardcodedComposeString`.
- **No inline colours or dp/sp outside `core/designsystem/`.** Use `MaterialTheme.colorScheme`,
  `MaterialTheme.typography`, `ForegroundTheme.spacing`. Enforced by detekt `fg2:InlineDesignValue`.
- **`core/domain` stays pure Kotlin.** No `android.*`/`androidx.*` imports. It returns state transitions; the
  caller (service/ViewModel) logs them.
- **Log through `core/logging/Log.kt`** (`Log.d(Log.Area.Gate) { "Idle -> Breathing pkg=..." }`). Tag is
  `FG2`, on in debug, off in release. Log every gate state transition and every overlay show/hide.
- **Time through `Clock` and `Timing`.** Never call `System.currentTimeMillis()` or read timing constants
  directly; inject `Clock` (use `FakeClock` in tests) and `Timing`.
  Debug: breath 10 s, window 1 min, poll 500 ms. Release: breath 60 s, window 15 min, poll 500 ms.
- Unit tests: JUnit 5 + kotlinx-coroutines-test + Turbine, under `app/src/test/`.

## Commands

```sh
./gradlew assembleDebug                # build
./gradlew detekt ktlintCheck           # static analysis (ktlintFormat auto-fixes style)
./gradlew testDebugUnitTest            # unit tests (:detekt-rules:test for the lint rules)
./gradlew installDebug                 # install on the connected device (Infinix X6871)
adb logcat -s FG2                      # app logs
```

Run detekt, ktlintCheck and testDebugUnitTest before considering a change done.
