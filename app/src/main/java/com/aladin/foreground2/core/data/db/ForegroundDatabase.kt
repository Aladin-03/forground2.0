package com.aladin.foreground2.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [WatchedAppEntity::class, LockEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: watched_apps.label
        AutoMigration(from = 1, to = 2),
        // v3: locks.endsAtMillis -> locks.unlockAtMillis
        AutoMigration(from = 2, to = 3, spec = ForegroundDatabase.RenameLockEnd::class),
    ],
)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun watchedAppDao(): WatchedAppDao

    abstract fun lockDao(): LockDao

    @RenameColumn(tableName = "locks", fromColumnName = "endsAtMillis", toColumnName = "unlockAtMillis")
    class RenameLockEnd : AutoMigrationSpec

    companion object {
        const val NAME = "foreground2.db"
    }
}
