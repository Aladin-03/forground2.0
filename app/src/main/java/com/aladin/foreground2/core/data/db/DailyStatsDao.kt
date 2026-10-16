package com.aladin.foreground2.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface DailyStatsDao {
    @Query("SELECT date FROM daily_stats")
    suspend fun savedDates(): List<String>

    @Query("SELECT * FROM daily_stats ORDER BY date")
    suspend fun allDays(): List<DailyStatsEntity>

    @Query("SELECT * FROM daily_app_usage")
    suspend fun allAppUsage(): List<DailyAppUsageEntity>

    @Upsert
    suspend fun upsertStats(stats: DailyStatsEntity)

    @Query("DELETE FROM daily_app_usage WHERE date = :date")
    suspend fun deleteAppUsage(date: String)

    @Upsert
    suspend fun upsertAppUsage(apps: List<DailyAppUsageEntity>)

    /** Replaces everything stored for [stats]'s date, so saving a day again is safe. */
    @Transaction
    suspend fun upsertDay(
        stats: DailyStatsEntity,
        apps: List<DailyAppUsageEntity>,
    ) {
        upsertStats(stats)
        deleteAppUsage(stats.date)
        upsertAppUsage(apps)
    }
}
