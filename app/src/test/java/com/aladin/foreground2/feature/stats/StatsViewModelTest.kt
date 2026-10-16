package com.aladin.foreground2.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.test
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.usage.AppUsage
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val MINUTE = 60_000L

private class FakeUsageStatsRepository(
    var usage: DailyUsage,
) : UsageStatsRepository {
    override suspend fun today(): DailyUsage = usage
}

private class FakeLauncherAppSource(
    private val labels: Map<String, String>,
) : LauncherAppSource {
    override suspend fun launcherApps(): List<AppInfo> = labels.map { AppInfo(it.key, it.value) }

    override suspend fun icon(packageName: String): ImageBitmap? = null

    override suspend fun label(packageName: String): String? = labels[packageName]
}

class StatsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `refresh shows today's stats with labels, falling back to the package name`() =
        runTest {
            val repo =
                FakeUsageStatsRepository(
                    DailyUsage(
                        unlocks = 12,
                        apps = listOf(AppUsage("com.youtube", 40 * MINUTE), AppUsage("com.hidden", 5 * MINUTE)),
                    ),
                )
            val viewModel = StatsViewModel(repo, FakeLauncherAppSource(mapOf("com.youtube" to "YouTube")))
            viewModel.uiState.test {
                assertEquals(StatsUiState(), awaitItem())
                viewModel.refresh()
                assertEquals(
                    StatsUiState(
                        loading = false,
                        unlocks = 12,
                        totalMillis = 45 * MINUTE,
                        apps =
                            listOf(
                                AppTime(AppInfo("com.youtube", "YouTube"), 40 * MINUTE),
                                AppTime(AppInfo("com.hidden", "com.hidden"), 5 * MINUTE),
                            ),
                    ),
                    awaitItem(),
                )
            }
        }
}
