package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 3 → 4.
 *
 * ## Changes
 * 1. Creates the `buffered_trips` table used by [TripBufferStore] as a
 *    write-ahead buffer for trips that failed to commit to the main tables.
 *
 * ## Safety
 * No existing tables are modified.  The new table is entirely additive.
 */
val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `buffered_trips` (
                `id`                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `trip_json`         TEXT    NOT NULL,
                `route_points_json` TEXT    NOT NULL,
                `created_at`        INTEGER NOT NULL,
                `retry_count`       INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
