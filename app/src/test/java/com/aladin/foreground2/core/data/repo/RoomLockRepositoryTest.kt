package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.data.db.LockDao
import com.aladin.foreground2.core.data.db.LockEntity
import com.aladin.foreground2.core.domain.FakeClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val YOUTUBE = "com.google.android.youtube"
private const val INSTAGRAM = "com.instagram.android"
private const val NOW = 1_700_000_000_000L
private const val HOUR = 3_600_000L

class RoomLockRepositoryTest {
    /** Implements only the queries, so [LockDao.upsertKeepingLater]'s real default body runs. */
    private class FakeLockDao : LockDao {
        val rows = mutableMapOf<String, LockEntity>()

        override fun observeAll() = flowOf(rows.values.sortedBy { it.unlockAtMillis })

        override suspend fun activeAt(nowMillis: Long) = rows.values.filter { it.unlockAtMillis > nowMillis }

        override suspend fun deleteEndedAt(nowMillis: Long): Int {
            val ended = rows.values.filter { it.unlockAtMillis <= nowMillis }
            ended.forEach { rows.remove(it.packageName) }
            return ended.size
        }

        override suspend fun get(packageName: String) = rows[packageName]

        override suspend fun upsert(lock: LockEntity) {
            rows[lock.packageName] = lock
        }
    }

    private val dao = FakeLockDao()
    private val repo = RoomLockRepository(dao, FakeClock(NOW))

    @Test
    fun `locking a new app stores its unlock time`() =
        runTest {
            assertEquals(NOW + HOUR, repo.lock(YOUTUBE, NOW + HOUR))
            assertEquals(LockEntity(YOUTUBE, NOW + HOUR), dao.rows[YOUTUBE])
        }

    @Test
    fun `a later lock extends an existing one`() =
        runTest {
            repo.lock(YOUTUBE, NOW + HOUR)
            assertEquals(NOW + 2 * HOUR, repo.lock(YOUTUBE, NOW + 2 * HOUR))
            assertEquals(NOW + 2 * HOUR, dao.rows.getValue(YOUTUBE).unlockAtMillis)
        }

    @Test
    fun `an earlier lock keeps the existing later end`() =
        runTest {
            repo.lock(YOUTUBE, NOW + 2 * HOUR)
            assertEquals(NOW + 2 * HOUR, repo.lock(YOUTUBE, NOW + HOUR))
            assertEquals(NOW + 2 * HOUR, dao.rows.getValue(YOUTUBE).unlockAtMillis)
        }

    @Test
    fun `active locks leave out expired ones`() =
        runTest {
            dao.rows[YOUTUBE] = LockEntity(YOUTUBE, NOW + HOUR)
            dao.rows[INSTAGRAM] = LockEntity(INSTAGRAM, NOW)
            assertEquals(mapOf(YOUTUBE to NOW + HOUR), repo.activeLocks())
        }

    @Test
    fun `deleting ended locks keeps running ones`() =
        runTest {
            dao.rows[YOUTUBE] = LockEntity(YOUTUBE, NOW + 1)
            dao.rows[INSTAGRAM] = LockEntity(INSTAGRAM, NOW)
            repo.deleteEnded()
            assertEquals(mapOf(YOUTUBE to LockEntity(YOUTUBE, NOW + 1)), dao.rows)
        }
}
