package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds durable checkpoints for an in-progress GPS tracking session.
 */
val Migration12To13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_tracking_sessions` (
                `id` INTEGER NOT NULL,
                `owner_uid` TEXT NOT NULL,
                `started_at` INTEGER NOT NULL,
                `detection_source` TEXT NOT NULL,
                `battery_level_at_start` INTEGER,
                `battery_saver_seen` INTEGER NOT NULL,
                `is_manual` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_route_points` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `session_id` INTEGER NOT NULL,
                `lat` REAL NOT NULL,
                `lng` REAL NOT NULL,
                `altitude_metres` REAL,
                `accuracy_metres` REAL,
                `speed_kph` REAL,
                `timestamp_ms` INTEGER NOT NULL,
                FOREIGN KEY(`session_id`) REFERENCES `active_tracking_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_active_route_points_session_id_timestamp_ms` " +
                "ON `active_route_points` (`session_id`, `timestamp_ms`)"
        )
    }
}
