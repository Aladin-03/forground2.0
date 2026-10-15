package com.aladin.foreground2.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedAppDao {
    @Query("SELECT * FROM watched_apps")
    fun observeAll(): Flow<List<WatchedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: WatchedAppEntity)

    @Query("DELETE FROM watched_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
