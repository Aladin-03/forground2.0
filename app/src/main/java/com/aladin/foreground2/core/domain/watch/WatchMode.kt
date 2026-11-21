package com.aladin.foreground2.core.domain.watch

/**
 * How closely a watched app is guarded: how long its allowed window lasts after each breath. The breath is the same
 * for every mode. Declared loosest first, so a later entry is stricter. [key] is the stable stored form (the database
 * column and navigation arguments), independent of the enum's name, which R8 may rename.
 */
enum class WatchMode(
    val key: String,
) {
    MINDFUL("MINDFUL"),
    FOCUSED("FOCUSED"),
    STRICT("STRICT"),
    ;

    /** True if this mode's window is shorter than [other]'s. */
    fun isStricterThan(other: WatchMode): Boolean = ordinal > other.ordinal

    companion object {
        /** The mode a watched app had before modes existed, and the database default. */
        val DEFAULT = MINDFUL

        fun fromKey(key: String): WatchMode? = entries.firstOrNull { it.key == key }
    }
}

/** A watched app with its mode (spec rule 1). */
data class WatchedApp(
    val packageName: String,
    val label: String,
    val mode: WatchMode,
)
