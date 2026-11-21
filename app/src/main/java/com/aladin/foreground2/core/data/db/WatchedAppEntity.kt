package com.aladin.foreground2.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app the user chose to watch (spec rule 1). [label] is the launcher label when it was picked. [mode] is a
 * WatchMode key; rows from before modes existed got MINDFUL ([ForegroundDatabase.MIGRATION_4_5]).
 */
@Entity(tableName = "watched_apps")
data class WatchedAppEntity(
    @PrimaryKey val packageName: String,
    @ColumnInfo(defaultValue = "") val label: String,
    @ColumnInfo(defaultValue = ForegroundDatabase.DEFAULT_MODE) val mode: String,
)
