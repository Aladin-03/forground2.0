package com.aladin.foreground2.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WatchedAppEntity::class, LockEntity::class, DailyStatsEntity::class, DailyAppUsageEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: watched_apps.label
        AutoMigration(from = 1, to = 2),
        // v3: locks.endsAtMillis -> locks.unlockAtMillis
        AutoMigration(from = 2, to = 3, spec = ForegroundDatabase.RenameLockEnd::class),
        // v4: daily_stats, daily_app_usage
        AutoMigration(from = 3, to = 4),
        // v5: watched_apps.mode is a hand-written Migration, MIGRATION_4_5, added in di/DataModule.
    ],
)
@TypeConverters(Converters::class)
abstract class ForegroundDatabase : RoomDatabase() {
    abstract fun watchedAppDao(): WatchedAppDao

    abstract fun lockDao(): LockDao

    abstract fun dailyStatsDao(): DailyStatsDao

    @RenameColumn(tableName = "locks", fromColumnName = "endsAtMillis", toColumnName = "unlockAtMillis")
    class RenameLockEnd : AutoMigrationSpec

    companion object {
        const val NAME = "foreground2.db"

        /** WatchMode.MINDFUL's key. A literal, since a column default must be a constant. */
        const val DEFAULT_MODE = "MINDFUL"

        /**
         * v5: each watched app gets a mode. Existing apps keep the 10-minute window they had, so they become MINDFUL.
         * Only adds a column: watched apps, locks and the usage history are left as they are.
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `watched_apps` ADD COLUMN `mode` TEXT NOT NULL DEFAULT '$DEFAULT_MODE'")
                }
            }
    }
}
