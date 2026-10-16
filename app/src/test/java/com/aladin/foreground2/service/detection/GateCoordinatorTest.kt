package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.gate.GateEffect.DeleteLock
import com.aladin.foreground2.core.domain.gate.GateEffect.HideOverlay
import com.aladin.foreground2.core.domain.gate.GateEffect.PersistLock
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowBreath
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowDurationPicker
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowLockScreen
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowPrompt
import com.aladin.foreground2.core.domain.gate.GateState.Allowed
import com.aladin.foreground2.core.domain.gate.GateState.Breathing
import com.aladin.foreground2.core.domain.gate.GateState.Idle
import com.aladin.foreground2.core.domain.gate.GateState.Locked
import com.aladin.foreground2.core.domain.gate.GateState.PickingDuration
import com.aladin.foreground2.core.domain.gate.GateState.Prompting
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val YOUTUBE = "com.google.android.youtube"
private const val INSTAGRAM = "com.instagram.android"
private const val LAUNCHER = "com.transsion.hilauncher"
private const val WHATSAPP = "com.whatsapp"
private const val START = 1_700_000_000_000L
private const val HOUR = 3_600_000L
private val TIMING =
    Timing(
        breathDurationMillis = 10_000L,
        allowedWindowMillis = 60_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
    )

/** In-memory [LockRepository] with the same keep-the-later-end rule as the Room one. */
private class FakeLockRepository(
    private val clock: FakeClock,
) : LockRepository {
    val stored = mutableMapOf<String, Long>()

    override suspend fun activeLocks(): Map<String, Long> = stored.filterValues { it > clock.now }

    override fun observeLocks() = flowOf(stored.toMap())

    override suspend fun lock(
        packageName: String,
        unlockAtMillis: Long,
    ): Long = maxOf(unlockAtMillis, stored[packageName] ?: Long.MIN_VALUE).also { stored[packageName] = it }

    override suspend fun deleteEnded() {
        stored.values.removeAll { it <= clock.now }
    }
}

class GateCoordinatorTest {
    private val clock = FakeClock(now = START)
    private val locks = FakeLockRepository(clock)
    private val coordinator = GateCoordinator(TIMING, clock, locks)

    @BeforeEach
    fun watch() =
        runTest {
            coordinator.handle(
                GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
            )
        }

    private suspend fun front(
        pkg: String,
        at: Long = clock.now,
    ) = coordinator.handle(GateInput.FrontChanged(FrontAppChanged(pkg, at)))

    private suspend fun tick() = coordinator.handle(GateInput.Tick(clock.now))

    @Test
    fun `entering a watched app starts a breath`() =
        runTest {
            val updates = front(YOUTUBE)
            assertEquals(listOf(GateUpdate(YOUTUBE, Idle, Breathing(START), listOf(ShowBreath), "YouTube")), updates)
            assertEquals(Breathing(START), coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `leaving for the launcher ends the breath`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(3_000)
            val updates = front(LAUNCHER)
            assertEquals(listOf(GateUpdate(YOUTUBE, Breathing(START), Idle, listOf(HideOverlay), "YouTube")), updates)
        }

    @Test
    fun `unwatched apps are never touched`() =
        runTest {
            assertTrue(front(WHATSAPP).isEmpty())
            assertTrue(front(LAUNCHER).isEmpty())
            assertTrue(tick().isEmpty())
        }

    @Test
    fun `switching between two watched apps leaves one and enters the other`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(1_000)
            val updates = front(INSTAGRAM)
            assertEquals(
                listOf(YOUTUBE to Idle, INSTAGRAM to Breathing(START + 1_000)),
                updates.map {
                    it.packageName to
                        it.to
                },
            )
        }

    @Test
    fun `coming back from another app is a fresh entry`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(4_000)
            front(WHATSAPP)
            clock.advanceBy(1_000)
            front(YOUTUBE)
            assertEquals(Breathing(START + 5_000), coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `ticks reach only the watched app in front and are silent when nothing changes`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(500)
            assertTrue(tick().isEmpty())
            assertEquals(Breathing(START), coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `unwatching the app mid-breath hides the overlay and forgets it`() =
        runTest {
            front(YOUTUBE)
            val updates = coordinator.handle(GateInput.WatchedChanged(listOf(AppInfo(INSTAGRAM, "Instagram"))))
            assertEquals(listOf(GateUpdate(YOUTUBE, Breathing(START), Idle, listOf(HideOverlay), "YouTube")), updates)
            assertEquals(Idle, coordinator.stateOf(YOUTUBE))
            assertTrue(front(LAUNCHER).isEmpty())
        }

    @Test
    fun `a finished breath moves the app to the prompt`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(TIMING.breathDurationMillis)
            val updates = coordinator.handle(GateInput.BreathFinished(YOUTUBE, clock.now))
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Breathing(START), Prompting, listOf(ShowPrompt), "YouTube")),
                updates,
            )
        }

    @Test
    fun `a breath finishing after the user left and came back is ignored`() =
        runTest {
            front(YOUTUBE)
            clock.advanceBy(3_000)
            front(LAUNCHER)
            front(YOUTUBE)
            clock.advanceBy(TIMING.breathDurationMillis - 3_000)
            assertTrue(coordinator.handle(GateInput.BreathFinished(YOUTUBE, clock.now)).isEmpty())
            assertEquals(Breathing(START + 3_000), coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `a breath finishing for an unwatched app is ignored`() =
        runTest {
            assertTrue(coordinator.handle(GateInput.BreathFinished(WHATSAPP, clock.now)).isEmpty())
        }

    /** Enters [pkg] and lets the breath finish, leaving it on the prompt. */
    private suspend fun prompt(pkg: String) {
        front(pkg)
        clock.advanceBy(TIMING.breathDurationMillis)
        coordinator.handle(GateInput.BreathFinished(pkg, clock.now))
    }

    @Test
    fun `No hides the overlay and allows the app for the window`() =
        runTest {
            prompt(YOUTUBE)
            val updates = coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
            val until = clock.now + TIMING.allowedWindowMillis
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Prompting, Allowed(until), listOf(HideOverlay), "YouTube")),
                updates,
            )
        }

    @Test
    fun `an allowed window ending while in front breathes on the spot`() =
        runTest {
            prompt(YOUTUBE)
            coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
            val until = clock.now + TIMING.allowedWindowMillis
            clock.advanceBy(TIMING.allowedWindowMillis - 1)
            assertTrue(tick().isEmpty())
            clock.advanceBy(1)
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Allowed(until), Breathing(until), listOf(ShowBreath), "YouTube")),
                tick(),
            )
        }

    @Test
    fun `leaving during the allowed window ends it and the next entry breathes`() =
        runTest {
            prompt(YOUTUBE)
            coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
            clock.advanceBy(5_000)
            front(LAUNCHER)
            assertEquals(Idle, coordinator.stateOf(YOUTUBE))
            val updates = front(YOUTUBE)
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Idle, Breathing(clock.now), listOf(ShowBreath), "YouTube")),
                updates,
            )
        }

    @Test
    fun `Yes moves the app to the duration picker and leaving returns it to Idle`() =
        runTest {
            prompt(YOUTUBE)
            val updates = coordinator.handle(GateInput.AnsweredYes(YOUTUBE))
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Prompting, PickingDuration, listOf(ShowDurationPicker), "YouTube")),
                updates,
            )
            assertEquals(
                listOf(GateUpdate(YOUTUBE, PickingDuration, Idle, listOf(HideOverlay), "YouTube")),
                front(LAUNCHER),
            )
        }

    @Test
    fun `a second tap after answering is ignored`() =
        runTest {
            prompt(YOUTUBE)
            coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
            assertTrue(coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now)).isEmpty())
            assertTrue(coordinator.handle(GateInput.AnsweredYes(YOUTUBE)).isEmpty())
        }

    @Test
    fun `answers for an unwatched app are ignored`() =
        runTest {
            assertTrue(coordinator.handle(GateInput.AnsweredNo(WHATSAPP, clock.now)).isEmpty())
            assertTrue(coordinator.handle(GateInput.AnsweredYes(WHATSAPP)).isEmpty())
        }

    /** Takes [pkg] through breath, prompt and Yes, leaving it on the duration picker. */
    private suspend fun picker(pkg: String) {
        prompt(pkg)
        coordinator.handle(GateInput.AnsweredYes(pkg))
    }

    @Test
    fun `Start persists the lock and shows the lock screen`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + 2 * HOUR
            val updates = coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            assertEquals(
                listOf(
                    GateUpdate(
                        YOUTUBE,
                        PickingDuration,
                        Locked(until),
                        listOf(PersistLock(until), ShowLockScreen(until)),
                        "YouTube",
                    ),
                ),
                updates,
            )
            assertEquals(mapOf(YOUTUBE to until), locks.stored)
        }

    @Test
    fun `a second Start is ignored and not persisted`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + HOUR
            coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            assertTrue(coordinator.handle(GateInput.LockStarted(YOUTUBE, until + HOUR)).isEmpty())
            assertEquals(mapOf(YOUTUBE to until), locks.stored)
        }

    @Test
    fun `Start for an unwatched app is ignored`() =
        runTest {
            assertTrue(coordinator.handle(GateInput.LockStarted(WHATSAPP, clock.now + HOUR)).isEmpty())
            assertTrue(locks.stored.isEmpty())
        }

    @Test
    fun `starting a shorter lock over an existing one keeps the later end`() =
        runTest {
            picker(YOUTUBE)
            val existing = clock.now + 5 * HOUR
            locks.stored[YOUTUBE] = existing
            val updates = coordinator.handle(GateInput.LockStarted(YOUTUBE, clock.now + HOUR))
            assertEquals(
                listOf(
                    GateUpdate(
                        YOUTUBE,
                        PickingDuration,
                        Locked(existing),
                        listOf(PersistLock(existing), ShowLockScreen(existing)),
                        "YouTube",
                    ),
                ),
                updates,
            )
            assertEquals(existing, locks.stored[YOUTUBE])
        }

    @Test
    fun `a restarted coordinator loads active locks and shows the lock screen on entry`() =
        runTest {
            val until = clock.now + HOUR
            locks.stored[YOUTUBE] = until
            locks.stored[INSTAGRAM] = clock.now - 1
            val restarted = GateCoordinator(TIMING, clock, locks)
            restarted.loadLocks()
            restarted.handle(
                GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
            )
            val updates = restarted.handle(GateInput.FrontChanged(FrontAppChanged(YOUTUBE, clock.now)))
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Locked(until), Locked(until), listOf(ShowLockScreen(until)), "YouTube")),
                updates,
            )
            assertEquals(Idle, restarted.stateOf(INSTAGRAM))
            assertEquals(mapOf(YOUTUBE to until), locks.stored)
        }

    @Test
    fun `reloading on a service restart in the same process drops in-memory gates but keeps locks`() =
        runTest {
            front(YOUTUBE)
            assertEquals(Breathing(START), coordinator.stateOf(YOUTUBE))
            val until = clock.now + HOUR
            locks.stored[INSTAGRAM] = until

            coordinator.loadLocks()
            coordinator.handle(
                GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
            )
            assertEquals(Idle, coordinator.stateOf(YOUTUBE))
            assertEquals(Locked(until), coordinator.stateOf(INSTAGRAM))
            // The detector starts over too, so the app in front is entered afresh: a new full breath.
            clock.advanceBy(TIMING.pollIntervalMillis)
            assertEquals(
                listOf(GateUpdate(YOUTUBE, Idle, Breathing(clock.now), listOf(ShowBreath), "YouTube")),
                front(YOUTUBE),
            )
        }

    @Test
    fun `a lock ending under its lock screen is deleted without a breath, and the next entry breathes`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + HOUR
            coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            clock.advanceBy(HOUR - 1)
            assertTrue(tick().isEmpty())
            clock.advanceBy(1)
            assertEquals(listOf(GateUpdate(YOUTUBE, Locked(until), Idle, listOf(DeleteLock), "YouTube")), tick())
            assertTrue(locks.stored.isEmpty())
            clock.advanceBy(TIMING.pollIntervalMillis)
            assertTrue(tick().isEmpty())
            assertEquals(listOf(GateUpdate(YOUTUBE, Idle, Idle, listOf(HideOverlay), "YouTube")), front(LAUNCHER))
            assertEquals(listOf(ShowBreath), front(YOUTUBE).single().effects)
        }

    @Test
    fun `a lock ending while its app is elsewhere is deleted on the next tick`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + HOUR
            coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            front(INSTAGRAM)
            clock.advanceBy(HOUR)
            val updates = tick()
            assertEquals(GateUpdate(YOUTUBE, Locked(until), Idle, listOf(DeleteLock), "YouTube"), updates.first())
            assertTrue(locks.stored.isEmpty())
            assertEquals(Idle, coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `an unwatched app's ended lock is deleted on the next tick and forgotten`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + HOUR
            coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            front(LAUNCHER)
            coordinator.handle(GateInput.WatchedChanged(listOf(AppInfo(INSTAGRAM, "Instagram"))))
            clock.advanceBy(HOUR)
            assertTrue(tick().isEmpty())
            assertTrue(locks.stored.isEmpty())
            coordinator.handle(
                GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
            )
            assertEquals(Idle, coordinator.stateOf(YOUTUBE))
        }

    @Test
    fun `unwatching and rewatching a locked app keeps the lock`() =
        runTest {
            picker(YOUTUBE)
            val until = clock.now + HOUR
            coordinator.handle(GateInput.LockStarted(YOUTUBE, until))
            front(LAUNCHER)
            coordinator.handle(GateInput.WatchedChanged(listOf(AppInfo(INSTAGRAM, "Instagram"))))
            coordinator.handle(
                GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
            )
            assertEquals(Locked(until), coordinator.stateOf(YOUTUBE))
        }
}
