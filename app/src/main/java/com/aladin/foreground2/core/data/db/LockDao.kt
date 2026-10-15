package com.aladin.foreground2.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LockDao {
    @Query("SELECT * FROM locks")
    fun observeAll(): Flow<List<LockEntity>>

    @Query("SELECT * FROM locks WHERE packageName = :packageName")
    suspend fun get(packageName: String): LockEntity?

    @Upsert
    suspend fun upsert(lock: LockEntity)
}
