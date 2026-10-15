package com.aladin.foreground2.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An app the user chose to watch (spec rule 1). */
@Entity(tableName = "watched_apps")
data class WatchedAppEntity(
    @PrimaryKey val packageName: String,
)
