package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.data.db.LockDao
import com.aladin.foreground2.core.data.db.LockEntity
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Per-app locks, kept as their end time in epoch ms (spec rules 6, 8, 9). */
interface LockRepository {
    /** Package name -> unlock time, for every lock that hasn't ended yet. */
    suspend fun activeLocks(): Map<String, Long>

    /**
     * Locks [packageName] until [unlockAtMillis]. If it already has a lock ending later, that one is kept. Returns
     * the unlock time that is stored afterwards.
     */
    suspend fun lock(
        packageName: String,
        unlockAtMillis: Long,
    ): Long
}

@Singleton
class RoomLockRepository
    @Inject
    constructor(
        private val dao: LockDao,
        private val clock: Clock,
    ) : LockRepository {
        override suspend fun activeLocks(): Map<String, Long> =
            dao.activeAt(clock.nowMillis()).associate { it.packageName to it.unlockAtMillis }

        override suspend fun lock(
            packageName: String,
            unlockAtMillis: Long,
        ): Long {
            val stored = dao.upsertKeepingLater(LockEntity(packageName, unlockAtMillis)).unlockAtMillis
            Log.i(Log.Area.DB) {
                "lock pkg=$packageName unlockAt=$unlockAtMillis" +
                    if (stored != unlockAtMillis) " (kept later existing $stored)" else ""
            }
            return stored
        }
    }
