package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 2 → 3.
 *
 * ## Changes
 * 1. Creates the `route_points` table with a CASCADE FK to `drives.id`.
 * 2. Adds five `meta_*` columns to `drives` for [TrackingMetadataEmbedded].
 *
 * ## Safety
 * All new columns use `DEFAULT NULL` or `DEFAULT 0` so existing rows remain
 * valid without a data back-fill step. No existing columns are dropped or
 * renamed, preserving all user trip data.
 */
val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── 1. Create route_points table ──────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `route_points` (
                `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `trip_id`         INTEGER NOT NULL,
                `lat`             REAL    NOT NULL,
                `lng`             REAL    NOT NULL,
                `altitude_metres` REAL,
                `accuracy_metres` REAL,
                `speed_kph`       REAL,
                `timestamp_ms`    INTEGER NOT NULL,
                FOREIGN KEY(`trip_id`) REFERENCES `drives`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_route_points_trip_id` ON `route_points` (`trip_id`)"
        )
        db.execSQL(
            """
                CREATE INDEX IF NOT EXISTS `index_route_points_trip_id_timestamp_ms` 
                ON `route_points` (`trip_id`, `timestamp_ms`)
            """
        )

        // ── 2. Add TrackingMetadata columns to drives ───────────────────────────
        db.execSQL("ALTER TABLE `drives` ADD COLUMN `meta_detection_source`         TEXT    DEFAULT NULL")
        db.execSQL("ALTER TABLE `drives` ADD COLUMN `meta_confidence_score`         REAL    DEFAULT NULL")
        db.execSQL("ALTER TABLE `drives` ADD COLUMN `meta_battery_level_at_start`   INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `drives` ADD COLUMN `meta_battery_level_at_end`     INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `drives` ADD COLUMN `meta_was_battery_saver_active` INTEGER NOT NULL DEFAULT 0")
    }
}
