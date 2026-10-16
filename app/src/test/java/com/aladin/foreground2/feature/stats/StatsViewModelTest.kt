package com.aladin.foreground2.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.test
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.data.usage.HistoryTrigger
import com.aladin.foreground2.core.data.usage.UsageHistory
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.usage.AppUsage
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

private const val MINUTE = 60_000L

// A Friday; its week starts on Monday the 12th.
private val TODAY = LocalDate.of(2026, 10, 16)

private fun day(
    date: LocalDate,
    vararg apps: Pair<String, Long>,
) = DailyUsage(date, List(DailyUsage.HOURS) { 0 }, apps.map { AppUsage(it.first, it.second) })

private class FakeUsageStatsRepository(
    var today: DailyUsage,
    var past: List<DailyUsage>,
) : UsageStatsRepository {
    override suspend fun today(): DailyUsage = today

    override suspend fun pastDays(): List<DailyUsage> = past
}

private class FakeUsageHistory : UsageHistory {
    val triggers = mutableListOf<HistoryTrigger>()

    override suspend fun catchUp(
        trigger: HistoryTrigger,
        rewriteYesterday: Boolean,
    ): Int {
        triggers += trigger
        return 0
    }
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

    private val history = FakeUsageHistory()
    private val repo =
        FakeUsageStatsRepository(
            today = day(TODAY, "com.youtube" to 40 * MINUTE),
            past =
                listOf(
                    day(LocalDate.of(2026, 10, 1), "com.hidden" to 5 * MINUTE),
                    day(TODAY.minusDays(1), "com.youtube" to 10 * MINUTE),
                    // A stale row for today is replaced by the live numbers.
                    day(TODAY, "com.youtube" to MINUTE),
                ),
        )

    private fun viewModel() = StatsViewModel(repo, history, FakeLauncherAppSource(mapOf("com.youtube" to "YouTube")))

    @Test
    fun `refresh saves missing days, then shows weeks from Room plus today live`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertEquals(StatsUiState(), awaitItem())
                viewModel.refresh()
                val state = awaitItem()
                assertEquals(listOf(HistoryTrigger.StatsOpened), history.triggers)
                assertEquals(
                    listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)),
                    state.weeks.map { it.start },
                )
                val current = state.weeks.last()
                assertEquals(40 * MINUTE, current.day(TODAY)?.totalMillis)
                assertEquals(10 * MINUTE, current.day(TODAY.minusDays(1))?.totalMillis)
                assertEquals("YouTube", state.label("com.youtube"))
                assertEquals("com.hidden", state.label("com.hidden"))
                assertEquals(mapOf("com.hidden" to 0, "com.youtube" to 1), state.colourSlots)
            }
        }

    @Test
    fun `selection defaults to today on the current week and Sunday on past weeks, and remembers taps`() =
        runTest {
            val viewModel = viewModel()
            viewModel.refresh()
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                val (past, _, current) = state.weeks
                assertEquals(TODAY, state.selectedDay(current))
                assertEquals(LocalDate.of(2026, 10, 4), state.selectedDay(past))

                viewModel.onSelectDay(current.start, TODAY.minusDays(2))
                assertEquals(TODAY.minusDays(2), awaitItem().selectedDay(current))
            }
        }
}
