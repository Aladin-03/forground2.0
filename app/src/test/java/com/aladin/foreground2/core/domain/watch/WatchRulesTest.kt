package com.aladin.foreground2.core.domain.watch

import com.aladin.foreground2.core.domain.watch.WatchMode.FOCUSED
import com.aladin.foreground2.core.domain.watch.WatchMode.MINDFUL
import com.aladin.foreground2.core.domain.watch.WatchMode.STRICT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val YOUTUBE = "com.google.android.youtube"

private fun decide(
    current: WatchMode?,
    request: WatchRequest,
    locked: Boolean = false,
) = WatchRules.decide(YOUTUBE, current, request, locked)

class WatchRulesTest {
    @Test
    fun `modes run from loosest to strictest`() {
        assertTrue(STRICT.isStricterThan(FOCUSED))
        assertTrue(FOCUSED.isStricterThan(MINDFUL))
        assertFalse(MINDFUL.isStricterThan(STRICT))
        assertFalse(FOCUSED.isStricterThan(FOCUSED))
    }

    @Test
    fun `keys round-trip and existing rows default to Mindful`() {
        WatchMode.entries.forEach { assertEquals(it, WatchMode.fromKey(it.key)) }
        assertEquals(MINDFUL, WatchMode.DEFAULT)
        assertEquals(null, WatchMode.fromKey("nonsense"))
    }

    @Nested
    inner class Enabling {
        @Test
        fun `an unwatched app is enabled at once in any mode`() {
            WatchMode.entries.forEach { assertEquals(WatchDecision.ApplyNow, decide(null, WatchRequest.Enable(it))) }
        }

        @Test
        fun `enabling works even while the app has a lock`() {
            assertEquals(WatchDecision.ApplyNow, decide(null, WatchRequest.Enable(MINDFUL), locked = true))
        }

        @Test
        fun `enabling a watched app changes nothing, so it can't sneak in a looser mode`() {
            assertEquals(WatchDecision.NoChange, decide(STRICT, WatchRequest.Enable(MINDFUL)))
        }
    }

    @Nested
    inner class Tightening {
        @Test
        fun `applies at once`() {
            assertEquals(WatchDecision.ApplyNow, decide(MINDFUL, WatchRequest.ChangeMode(FOCUSED)))
            assertEquals(WatchDecision.ApplyNow, decide(MINDFUL, WatchRequest.ChangeMode(STRICT)))
            assertEquals(WatchDecision.ApplyNow, decide(FOCUSED, WatchRequest.ChangeMode(STRICT)))
        }

        @Test
        fun `is still allowed while locked`() {
            assertEquals(WatchDecision.ApplyNow, decide(MINDFUL, WatchRequest.ChangeMode(STRICT), locked = true))
        }

        @Test
        fun `the same mode is no change`() {
            assertEquals(WatchDecision.NoChange, decide(FOCUSED, WatchRequest.ChangeMode(FOCUSED)))
        }

        @Test
        fun `changing the mode of an unwatched app is no change`() {
            assertEquals(WatchDecision.NoChange, decide(null, WatchRequest.ChangeMode(STRICT)))
        }
    }

    @Nested
    inner class Loosening {
        @Test
        fun `needs a completed countdown`() {
            assertEquals(
                WatchDecision.NeedsCountdown(PendingChange.Loosen(YOUTUBE, STRICT, MINDFUL)),
                decide(STRICT, WatchRequest.ChangeMode(MINDFUL)),
            )
            assertEquals(
                WatchDecision.NeedsCountdown(PendingChange.Loosen(YOUTUBE, STRICT, FOCUSED)),
                decide(STRICT, WatchRequest.ChangeMode(FOCUSED)),
            )
        }

        @Test
        fun `a locked app cannot be loosened`() {
            assertEquals(WatchDecision.BlockedByLock, decide(STRICT, WatchRequest.ChangeMode(MINDFUL), locked = true))
            assertEquals(WatchDecision.BlockedByLock, decide(FOCUSED, WatchRequest.ChangeMode(MINDFUL), locked = true))
        }
    }

    @Nested
    inner class Disabling {
        @Test
        fun `needs a completed countdown, in every mode`() {
            WatchMode.entries.forEach {
                assertEquals(
                    WatchDecision.NeedsCountdown(PendingChange.Disable(YOUTUBE, it)),
                    decide(it, WatchRequest.Disable),
                )
            }
        }

        @Test
        fun `a locked app cannot be disabled`() {
            WatchMode.entries.forEach {
                assertEquals(WatchDecision.BlockedByLock, decide(it, WatchRequest.Disable, locked = true))
            }
        }

        @Test
        fun `disabling an unwatched app is no change`() {
            assertEquals(WatchDecision.NoChange, decide(null, WatchRequest.Disable))
        }
    }

    @Nested
    inner class `Applying after the countdown` {
        private val loosen = PendingChange.Loosen(YOUTUBE, STRICT, MINDFUL)
        private val disable = PendingChange.Disable(YOUTUBE, FOCUSED)

        @Test
        fun `applies when nothing changed meanwhile`() {
            assertTrue(WatchRules.canApply(loosen, current = STRICT, locked = false))
            assertTrue(WatchRules.canApply(disable, current = FOCUSED, locked = false))
        }

        @Test
        fun `a lock started meanwhile blocks it`() {
            assertFalse(WatchRules.canApply(loosen, current = STRICT, locked = true))
            assertFalse(WatchRules.canApply(disable, current = FOCUSED, locked = true))
        }

        @Test
        fun `a mode changed meanwhile, or the app unwatched, voids it`() {
            assertFalse(WatchRules.canApply(loosen, current = FOCUSED, locked = false))
            assertFalse(WatchRules.canApply(disable, current = null, locked = false))
        }
    }
}
