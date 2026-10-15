package com.aladin.foreground2.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A per-app lock, stored as its end time in epoch ms (spec rule 9). */
@Entity(tableName = "locks")
data class LockEntity(
    @PrimaryKey val packageName: String,
    val endsAtMillis: Long,
)
