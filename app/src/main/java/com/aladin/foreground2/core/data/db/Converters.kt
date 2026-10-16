package com.aladin.foreground2.core.data.db

import androidx.room.TypeConverter

/** Room column types beyond the built-ins. */
class Converters {
    /** A list of ints as "1,0,3". */
    @TypeConverter
    fun intsToString(values: List<Int>): String = values.joinToString(",")

    @TypeConverter
    fun stringToInts(value: String): List<Int> =
        if (value.isEmpty()) emptyList() else value.split(',').map(String::toInt)
}
