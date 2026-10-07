package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 5 → 6.
 *
 * ## Changes
 * Creates the `odometer_captures` table introduced by Task 4.5.
 *
 * ### DDL
 * ```sql
 * CREATE TABLE IF NOT EXISTS odometer_captures (
 *     id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
 *     vehicle_id   INTEGER NOT NULL,
 *     capture_date INTEGER NOT NULL,
 *     reading_km   INTEGER NOT NULL,
 *     photo_uri    TEXT,
 *     capture_type TEXT    NOT NULL DEFAULT 'MANUAL',
 *     notes        TEXT,
 *     created_at   INTEGER NOT NULL,
 *     FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
 * );
 * CREATE INDEX index_odometer_captures_vehicle_id
 *     ON odometer_captures (vehicle_id);
 * CREATE INDEX index_odometer_captures_vehicle_id_capture_date
 *     ON odometer_captures (vehicle_id, capture_date);
 * ```
 */
val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `odometer_captures` (
                `id`           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `vehicle_id`   INTEGER NOT NULL,
                `capture_date` INTEGER NOT NULL,
                `reading_km`   INTEGER NOT NULL,
                `photo_uri`    TEXT,
                `capture_type` TEXT    NOT NULL DEFAULT 'MANUAL',
                `notes`        TEXT,
                `created_at`   INTEGER NOT NULL,
                FOREIGN KEY(`vehicle_id`) REFERENCES `vehicles`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_odometer_captures_vehicle_id` " +
            "ON `odometer_captures` (`vehicle_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_odometer_captures_vehicle_id_capture_date` " +
            "ON `odometer_captures` (`vehicle_id`, `capture_date`)"
        )
    }
}
