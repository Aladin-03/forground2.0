package com.aladin.foreground2.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WatchedAppEntity::class, LockEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun watchedAppDao(): WatchedAppDao

    abstract fun lockDao(): LockDao

    companion object {
        const val NAME = "foreground2.db"
    }
}
