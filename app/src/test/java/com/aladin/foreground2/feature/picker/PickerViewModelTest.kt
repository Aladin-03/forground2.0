package com.aladin.foreground2.feature.picker

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private class FakeLauncherAppSource(
    private val apps: List<AppInfo>,
) : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = apps

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = apps.find { it.packageName == packageName }?.label
}

private class FakeWatchedAppsRepository : WatchedAppsRepository {
    val watched = MutableStateFlow<List<AppInfo>>(emptyList())

    override fun observeWatched(): Flow<List<AppInfo>> = watched

    override suspend fun watch(app: AppInfo) {
        if (watched.value.none { it.packageName == app.packageName }) watched.value += app
    }

    override suspend fun unwatch(packageName: String) {
        watched.value = watched.value.filterNot { it.packageName == packageName }
    }
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
    private val repo = FakeWatchedAppsRepository()
    private val viewModel = PickerViewModel(FakeLauncherAppSource(listOf(instagram, chrome)), repo)

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
    fun `toggling on saves the app and moves it first, toggling off removes it`() =
        runTest {
            viewModel.uiState.test {
                awaitState { !it.loading }

                viewModel.onToggle(instagram, watched = true)
                assertEquals(
                    listOf(PickerItem(instagram, watched = true), PickerItem(chrome, watched = false)),
                    awaitState { it.items.any(PickerItem::watched) }.items,
                )
                assertEquals(listOf(instagram), repo.watched.value)

                viewModel.onToggle(instagram, watched = false)
                assertEquals(
                    listOf(chrome, instagram),
                    awaitState { it.items.none(PickerItem::watched) }.items.map { it.app },
                )
                assertTrue(repo.watched.value.isEmpty())
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
