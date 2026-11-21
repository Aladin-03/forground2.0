package com.aladin.foreground2.feature.home

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchChanges
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.feature.sitthrough.SitThroughRoute
import com.aladin.foreground2.service.detection.DetectionServiceController
import com.aladin.foreground2.testing.FakeLockRepository
import com.aladin.foreground2.testing.FakeWatchedAppsRepository
import com.aladin.foreground2.testing.MainDispatcherExtension
import com.aladin.foreground2.testing.watchedAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val NOW = 1_700_000_000_000L
private const val HOUR = 3_600_000L
private val YOUTUBE = AppInfo("com.google.android.youtube", "YouTube")
private val INSTAGRAM = AppInfo("com.instagram.android", "Instagram")
private val CHROME = AppInfo("com.android.chrome", "Chrome")
private val TIMING =
    Timing(
        breathDurationMillis = 10_000L,
        mindfulWindowMillis = 60_000L,
        focusedWindowMillis = 30_000L,
        strictWindowMillis = 10_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
        widgetRefreshIntervalMillis = 300_000L,
        widgetMinRefreshIntervalMillis = 60_000L,
        sitThroughMillis = 20_000L,
    )

private class FakePermissionChecker : PermissionChecker {
    val granted = Permission.entries.toMutableSet()

    override fun isGranted(permission: Permission): Boolean = permission in granted
}

private class FakeDetectionServiceController : DetectionServiceController {
    override val running = MutableStateFlow(false)
    var starts = 0

    override fun startIfReady(): Boolean {
        starts++
        return true
    }
}

private class FakeLauncherAppSource(
    private val apps: List<AppInfo>,
) : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = apps

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = apps.find { it.packageName == packageName }?.label
}

/** Waits for the first state matching [predicate], skipping intermediate ones. */
private suspend fun ReceiveTurbine<HomeUiState>.awaitState(predicate: (HomeUiState) -> Boolean): HomeUiState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
}

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val permissions = FakePermissionChecker()
    private val controller = FakeDetectionServiceController()
    private val clock = FakeClock(NOW)
    private val locks = FakeLockRepository(clock)
    private val watched = FakeWatchedAppsRepository()

    private fun viewModel() =
        HomeViewModel(
            permissions,
            controller,
            FakeLauncherAppSource(listOf(CHROME)),
            locks,
            WatchChanges(watched, locks, TIMING),
            clock,
        )

    @Test
    fun `lists running locks soonest first, labelled, without ended ones`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(), INSTAGRAM.watchedAs())
            locks.locks.value =
                mapOf(
                    YOUTUBE.packageName to NOW + 2 * HOUR,
                    INSTAGRAM.packageName to NOW + HOUR,
                    // Unwatched while locked: labelled from the package manager.
                    CHROME.packageName to NOW + 3 * HOUR,
                    // Ended but not deleted yet.
                    "com.ended" to NOW - 1,
                    // Uninstalled: falls back to the package name.
                    "com.gone" to NOW + 4 * HOUR,
                )
            viewModel().uiState.test {
                val state = awaitState { it.locks.isNotEmpty() }
                assertEquals(
                    listOf(
                        LockedApp(INSTAGRAM, NOW + HOUR),
                        LockedApp(YOUTUBE, NOW + 2 * HOUR),
                        LockedApp(CHROME, NOW + 3 * HOUR),
                        LockedApp(AppInfo("com.gone", "com.gone"), NOW + 4 * HOUR),
                    ),
                    state.locks,
                )
            }
        }

    @Test
    fun `a new lock shows up without a refresh`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs())
            viewModel().uiState.test {
                awaitState { it.locks.isEmpty() }
                locks.lock(YOUTUBE.packageName, NOW + HOUR)
                assertEquals(listOf(LockedApp(YOUTUBE, NOW + HOUR)), awaitState { it.locks.isNotEmpty() }.locks)
            }
        }

    @Test
    fun `watched apps are listed by label and marked locked only while their lock runs`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(), INSTAGRAM.watchedAs())
            locks.locks.value = mapOf(YOUTUBE.packageName to NOW + HOUR, INSTAGRAM.packageName to NOW - 1)
            viewModel().uiState.test {
                assertEquals(
                    listOf(
                        HomeWatchedApp(INSTAGRAM, WatchMode.MINDFUL, windowMillis = 60_000L),
                        HomeWatchedApp(YOUTUBE, WatchMode.MINDFUL, windowMillis = 60_000L, lockedUntil = NOW + HOUR),
                    ),
                    awaitState { it.watched.isNotEmpty() }.watched,
                )
            }
        }

    @Test
    fun `each watched app shows its own mode, live`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(WatchMode.STRICT), INSTAGRAM.watchedAs(WatchMode.FOCUSED))
            viewModel().uiState.test {
                assertEquals(
                    listOf(WatchMode.FOCUSED, WatchMode.STRICT),
                    awaitState { it.watched.isNotEmpty() }.watched.map { it.mode },
                )
                watched.setMode(INSTAGRAM.packageName, WatchMode.STRICT)
                awaitState { state -> state.watched.all { it.mode == WatchMode.STRICT } }
            }
        }

    @Test
    fun `tightening from the chip applies at once, with no countdown`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(WatchMode.MINDFUL))
            val viewModel = viewModel()
            viewModel.sitThrough.test {
                viewModel.onModePicked(YOUTUBE, WatchMode.STRICT)
                assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
                expectNoEvents()
            }
        }

    @Test
    fun `loosening from the chip opens the countdown and changes nothing yet`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(WatchMode.STRICT))
            val viewModel = viewModel()
            viewModel.sitThrough.test {
                viewModel.onModePicked(YOUTUBE, WatchMode.MINDFUL)
                assertEquals(SitThroughRoute(YOUTUBE.packageName, WatchMode.MINDFUL.key), awaitItem())
                assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
            }
        }

    @Test
    fun `a locked app can be tightened but not loosened`() =
        runTest {
            watched.watched.value = listOf(YOUTUBE.watchedAs(WatchMode.FOCUSED))
            locks.locks.value = mapOf(YOUTUBE.packageName to NOW + HOUR)
            val viewModel = viewModel()
            viewModel.sitThrough.test {
                viewModel.onModePicked(YOUTUBE, WatchMode.MINDFUL)
                expectNoEvents()
                assertEquals(WatchMode.FOCUSED, watched.modeOf(YOUTUBE.packageName))
                viewModel.onModePicked(YOUTUBE, WatchMode.STRICT)
                assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
            }
        }

    @Test
    fun `status follows the service and permission revocations seen on resume`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertEquals(HomeFix.StartService, awaitItem().fix)

                controller.running.value = true
                assertNull(awaitState { it.serviceRunning }.fix)

                permissions.granted -= Permission.UsageAccess
                viewModel.refresh()
                val state = awaitState { it.missing.isNotEmpty() }
                assertEquals(listOf(Permission.UsageAccess), state.missingRequired)
                assertEquals(HomeFix.Grant(Permission.UsageAccess), state.fix)
            }
        }

    @Test
    fun `start button starts the service`() {
        viewModel().onStartService()
        assertEquals(1, controller.starts)
    }
}

class HomeUiStateTest {
    @Test
    fun `fix is the most urgent problem`() {
        val battery = Permission.BatteryOptimization
        assertEquals(
            HomeFix.Grant(Permission.Overlay),
            HomeUiState(missing = listOf(battery, Permission.Overlay), serviceRunning = false).fix,
        )
        assertEquals(HomeFix.StartService, HomeUiState(missing = listOf(battery), serviceRunning = false).fix)
        assertEquals(HomeFix.Grant(battery), HomeUiState(missing = listOf(battery), serviceRunning = true).fix)
        assertNull(HomeUiState(serviceRunning = true).fix)
    }
}
