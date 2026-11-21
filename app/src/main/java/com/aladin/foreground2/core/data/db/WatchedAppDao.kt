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

    /** Ignored if the app is already watched: enabling never changes the mode of a watched app. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: WatchedAppEntity)

    /** Returns the number of rows changed: 0 if the app isn't watched. */
    @Query("UPDATE watched_apps SET mode = :mode WHERE packageName = :packageName")
    suspend fun setMode(
        packageName: String,
        mode: String,
    ): Int

    @Query("DELETE FROM watched_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
