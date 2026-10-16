package com.aladin.foreground2.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One finished day's totals. [date] is the local date as yyyy-MM-dd; [unlocksByHour] has 24 entries. */
@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val date: String,
    val unlocks: Int,
    val totalScreenMs: Long,
    val unlocksByHour: List<Int>,
)

/** One app's foreground time on one finished day. */
@Entity(tableName = "daily_app_usage", primaryKeys = ["date", "packageName"])
data class DailyAppUsageEntity(
    val date: String,
    val packageName: String,
    val foregroundMs: Long,
)
