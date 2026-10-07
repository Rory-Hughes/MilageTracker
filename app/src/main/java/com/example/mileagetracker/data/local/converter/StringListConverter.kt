package com.example.mileagetracker.data.local.converter

import androidx.room.TypeConverter

/**
 * Room [TypeConverter] for persisting a `List<String>` as a single
 * comma-separated `TEXT` column.
 *
 * Empty lists are stored as an empty string `""`.  Lists that contain
 * an entry with a comma in its value are **not** supported; callers
 * should use URL-encoding or a different delimiter if needed.
 */
class StringListConverter {

    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
