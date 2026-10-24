package com.aladin.foreground2.feature.lock

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.db.LockEntity
import com.aladin.foreground2.core.data.repo.RoomLockRepository
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.LockDuration
import com.aladin.foreground2.testing.FakeLockDao
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val NOW = 1_700_000_000_000L
private const val HOUR = 3_600_000L
private val YOUTUBE = AppInfo("com.google.android.youtube", "YouTube")

private class FakeLauncherAppSource : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = emptyList()

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = null
}

private class FakeWatchedAppsRepository(
    apps: List<AppInfo>,
) : WatchedAppsRepository {
    private val watched = MutableStateFlow(apps)

    override fun observeWatched(): Flow<List<AppInfo>> = watched

    override suspend fun watch(app: AppInfo) {
        watched.value += app
    }

    override suspend fun unwatch(packageName: String) {
        watched.value = watched.value.filterNot { it.packageName == packageName }
    }
}

/** Runs on the real [RoomLockRepository] over a fake DAO, so the keep-the-later-end rule is the real one. */
class LockAppViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val clock = FakeClock(NOW)
    private val dao = FakeLockDao()

    private fun viewModel() =
        LockAppViewModel(
            SavedStateHandle(mapOf(LockAppRoute.ARG_PACKAGE_NAME to YOUTUBE.packageName)),
            RoomLockRepository(dao, clock),
            FakeWatchedAppsRepository(listOf(YOUTUBE)),
            FakeLauncherAppSource(),
            clock,
        )

    @Test
    fun `an unlocked app opens the picker`() {
        assertEquals(LockAppUiState.Picking(YOUTUBE), viewModel().uiState.value)
    }

    @Test
    fun `Start stores the lock until now plus the duration and goes back`() {
        val viewModel = viewModel()
        viewModel.onStart(LockDuration(days = 0, hours = 2, minutes = 30))
        assertEquals(LockEntity(YOUTUBE.packageName, NOW + 2 * HOUR + HOUR / 2), dao.rows[YOUTUBE.packageName])
        assertEquals(LockAppUiState.Started, viewModel.uiState.value)
    }

    @Test
    fun `Start does not shorten a longer lock stored meanwhile`() {
        val viewModel = viewModel()
        // E.g. locked from the overlay after this screen opened.
        dao.rows[YOUTUBE.packageName] = LockEntity(YOUTUBE.packageName, NOW + 5 * HOUR)
        viewModel.onStart(LockDuration(days = 0, hours = 1, minutes = 0))
        assertEquals(NOW + 5 * HOUR, dao.rows.getValue(YOUTUBE.packageName).unlockAtMillis)
        assertEquals(LockAppUiState.Started, viewModel.uiState.value)
    }

    @Test
    fun `a locked app opens read-only and Start changes nothing`() {
        dao.rows[YOUTUBE.packageName] = LockEntity(YOUTUBE.packageName, NOW + 5 * HOUR)
        val viewModel = viewModel()
        assertEquals(LockAppUiState.Locked(YOUTUBE, NOW + 5 * HOUR), viewModel.uiState.value)
        viewModel.onStart(LockDuration(days = 30, hours = 0, minutes = 0))
        assertEquals(NOW + 5 * HOUR, dao.rows.getValue(YOUTUBE.packageName).unlockAtMillis)
        assertEquals(LockAppUiState.Locked(YOUTUBE, NOW + 5 * HOUR), viewModel.uiState.value)
    }

    @Test
    fun `an ended lock that wasn't deleted yet opens the picker`() {
        dao.rows[YOUTUBE.packageName] = LockEntity(YOUTUBE.packageName, NOW - 1)
        assertEquals(LockAppUiState.Picking(YOUTUBE), viewModel().uiState.value)
    }
}
