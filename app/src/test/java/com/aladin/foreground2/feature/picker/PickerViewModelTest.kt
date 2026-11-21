package com.aladin.foreground2.feature.picker

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchChanges
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.feature.sitthrough.SitThroughRoute
import com.aladin.foreground2.testing.FakeLockRepository
import com.aladin.foreground2.testing.FakeWatchedAppsRepository
import com.aladin.foreground2.testing.MainDispatcherExtension
import com.aladin.foreground2.testing.watchedAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val NOW = 1_700_000_000_000L
private const val HOUR = 3_600_000L
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

private class FakeLauncherAppSource(
    private val apps: List<AppInfo>,
) : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = apps

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = apps.find { it.packageName == packageName }?.label
}

/** Waits for the first state matching [predicate], skipping intermediate ones. */
private suspend fun ReceiveTurbine<PickerUiState>.awaitState(predicate: (PickerUiState) -> Boolean): PickerUiState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
}

class PickerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val chrome = AppInfo("com.android.chrome", "Chrome")
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val clock = FakeClock(NOW)
    private val repo = FakeWatchedAppsRepository()
    private val locks = FakeLockRepository(clock)
    private val viewModel =
        PickerViewModel(
            FakeLauncherAppSource(listOf(instagram, chrome)),
            repo,
            locks,
            WatchChanges(repo, locks, TIMING),
            clock,
        )

    @Test
    fun `lists launcher apps alphabetically once loaded`() =
        runTest {
            viewModel.uiState.test {
                assertTrue(awaitItem().loading)
                val state = awaitState { !it.loading }
                assertEquals(listOf(chrome, instagram), state.items.map { it.app })
            }
        }

    @Test
    fun `enabling with a picked mode saves the app in that mode and moves it first`() =
        runTest {
            viewModel.uiState.test {
                awaitState { !it.loading }
                viewModel.onEnable(instagram, WatchMode.STRICT)
                assertEquals(
                    listOf(PickerItem(instagram, mode = WatchMode.STRICT), PickerItem(chrome)),
                    awaitState { it.items.any(PickerItem::watched) }.items,
                )
                assertEquals(listOf(instagram.watchedAs(WatchMode.STRICT)), repo.watched.value)
            }
        }

    @Test
    fun `switching off asks for the countdown and leaves the app watched`() =
        runTest {
            repo.watched.value = listOf(instagram.watchedAs(WatchMode.FOCUSED))
            viewModel.sitThrough.test {
                viewModel.onDisable(instagram)
                assertEquals(SitThroughRoute(instagram.packageName), awaitItem())
            }
            assertEquals(WatchMode.FOCUSED, repo.modeOf(instagram.packageName))
        }

    @Test
    fun `a locked app can't be switched off and its row carries the lock`() =
        runTest {
            repo.watched.value = listOf(instagram.watchedAs())
            locks.locks.value = mapOf(instagram.packageName to NOW + HOUR)
            viewModel.sitThrough.test {
                viewModel.onDisable(instagram)
                expectNoEvents()
            }
            assertEquals(WatchMode.MINDFUL, repo.modeOf(instagram.packageName))
            viewModel.uiState.test {
                val row = awaitState { !it.loading }.items.first()
                assertEquals(PickerItem(instagram, WatchMode.MINDFUL, lockedUntil = NOW + HOUR), row)
            }
        }

    @Test
    fun `query filters the list`() =
        runTest {
            viewModel.uiState.test {
                awaitState { !it.loading }
                viewModel.onQueryChange("chr")
                assertEquals("chr", viewModel.query.value)
                assertEquals(listOf(chrome), awaitState { it.items.size == 1 }.items.map { it.app })
            }
        }
}
