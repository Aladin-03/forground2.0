package com.aladin.foreground2.feature.sitthrough

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchChanges
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.testing.FakeLockRepository
import com.aladin.foreground2.testing.FakeWatchedAppsRepository
import com.aladin.foreground2.testing.MainDispatcherExtension
import com.aladin.foreground2.testing.watchedAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val NOW = 1_700_000_000_000L
private const val COUNTDOWN = 300_000L
private const val HOUR = 3_600_000L
private val YOUTUBE = AppInfo("com.google.android.youtube", "YouTube")
private val TIMING =
    Timing(
        breathDurationMillis = 60_000L,
        mindfulWindowMillis = 600_000L,
        focusedWindowMillis = 300_000L,
        strictWindowMillis = 60_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
        widgetRefreshIntervalMillis = 300_000L,
        widgetMinRefreshIntervalMillis = 60_000L,
        sitThroughMillis = COUNTDOWN,
    )

private class FakeLauncherAppSource : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = emptyList()

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = null
}

class SitThroughViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = FakeClock(NOW)
    private val watched = FakeWatchedAppsRepository(listOf(YOUTUBE.watchedAs(WatchMode.STRICT)))
    private val locks = FakeLockRepository(clock)

    private fun viewModel(
        to: WatchMode? = null,
        restored: Boolean = false,
    ): SitThroughViewModel {
        val args =
            buildMap<String, Any?> {
                put(SitThroughRoute.ARG_PACKAGE_NAME, YOUTUBE.packageName)
                put(SitThroughRoute.ARG_MODE_KEY, to?.key)
                if (restored) put(SitThroughViewModel.KEY_STARTED, true)
            }
        return SitThroughViewModel(
            SavedStateHandle(args),
            WatchChanges(watched, locks, TIMING),
            watched,
            FakeLauncherAppSource(),
            clock,
            TIMING,
        )
    }

    /** Lets [millis] pass on both the clock and the countdown's delays. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pass(millis: Long) {
        clock.advanceBy(millis)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(millis)
        mainDispatcher.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `disabling starts a full countdown and changes nothing yet`() {
        val viewModel = viewModel()
        assertEquals(
            SitThroughUiState.Running(YOUTUBE.packageName, "YouTube", to = null, endsAt = NOW + COUNTDOWN),
            viewModel.uiState.value,
        )
        pass(COUNTDOWN - 1_000)
        assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
    }

    @Test
    fun `a completed countdown unwatches the app and closes`() {
        val viewModel = viewModel()
        pass(COUNTDOWN)
        assertEquals(null, watched.modeOf(YOUTUBE.packageName))
        assertEquals(SitThroughUiState.Finished, viewModel.uiState.value)
    }

    @Test
    fun `a completed countdown loosens the app`() {
        viewModel(to = WatchMode.MINDFUL)
        pass(COUNTDOWN)
        assertEquals(WatchMode.MINDFUL, watched.modeOf(YOUTUBE.packageName))
    }

    @Test
    fun `a cancelled countdown changes nothing, even after its end time`() {
        val viewModel = viewModel(to = WatchMode.MINDFUL)
        pass(COUNTDOWN - 1_000)
        viewModel.onLeave(SitThroughViewModel.LeaveReason.LeftScreen)
        assertEquals(SitThroughUiState.Finished, viewModel.uiState.value)
        pass(HOUR)
        assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
    }

    @Test
    fun `the next attempt starts again from the full duration`() {
        viewModel().onLeave(SitThroughViewModel.LeaveReason.LeftScreen)
        pass(60_000)
        val again = viewModel()
        assertEquals(clock.now + COUNTDOWN, (again.uiState.value as SitThroughUiState.Running).endsAt)
    }

    @Test
    fun `a countdown restored after process death is cancelled`() {
        val viewModel = viewModel(restored = true)
        assertEquals(SitThroughUiState.Finished, viewModel.uiState.value)
        pass(COUNTDOWN)
        assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
    }

    @Test
    fun `a locked app gets no countdown and nothing changes`() {
        locks.locks.value = mapOf(YOUTUBE.packageName to NOW + HOUR)
        val viewModel = viewModel()
        assertEquals(SitThroughUiState.Finished, viewModel.uiState.value)
        assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
    }

    @Test
    fun `a lock started during the countdown stops it from applying`() {
        viewModel(to = WatchMode.MINDFUL)
        pass(1_000)
        locks.locks.value = mapOf(YOUTUBE.packageName to NOW + HOUR)
        pass(COUNTDOWN)
        assertEquals(WatchMode.STRICT, watched.modeOf(YOUTUBE.packageName))
    }
}
