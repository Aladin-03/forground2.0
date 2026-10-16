package com.aladin.foreground2.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface LockDao {
    /** Locks still running at [nowMillis]. */
    @Query("SELECT * FROM locks WHERE unlockAtMillis > :nowMillis")
    suspend fun activeAt(nowMillis: Long): List<LockEntity>

    /** Deletes every lock that has ended by [nowMillis]; returns how many. */
    @Query("DELETE FROM locks WHERE unlockAtMillis <= :nowMillis")
    suspend fun deleteEndedAt(nowMillis: Long): Int

    @Query("SELECT * FROM locks WHERE packageName = :packageName")
    suspend fun get(packageName: String): LockEntity?

    @Upsert
    suspend fun upsert(lock: LockEntity)

    /**
     * Stores [lock] unless the app already has a lock ending later (rule 6: a lock can't be shortened). Returns the
     * row that is stored afterwards.
     */
    @Transaction
    suspend fun upsertKeepingLater(lock: LockEntity): LockEntity {
        val existing = get(lock.packageName)
        if (existing != null && existing.unlockAtMillis >= lock.unlockAtMillis) return existing
        upsert(lock)
        return lock
    }
}
