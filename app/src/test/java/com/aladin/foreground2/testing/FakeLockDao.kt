package com.aladin.foreground2.testing

import com.aladin.foreground2.core.data.db.LockDao
import com.aladin.foreground2.core.data.db.LockEntity
import kotlinx.coroutines.flow.flowOf

/** Implements only the queries, so [LockDao.upsertKeepingLater]'s real default body runs. */
class FakeLockDao : LockDao {
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
