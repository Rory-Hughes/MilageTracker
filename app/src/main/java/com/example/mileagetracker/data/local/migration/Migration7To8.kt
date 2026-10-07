package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration: version 7 → 8.
 *
 * ## Changes
 * - Adds table `report_history` for CRA logbook export metadata (Task 5.3).
 *
 * Column mapping:
 * | Column          | Type    | Notes                                          |
 * |-----------------|---------|------------------------------------------------|
 * | id              | INTEGER | PK auto-increment                              |
 * | format          | TEXT    | NOT NULL — "PDF" or "CSV" (ExportFormat.name)  |
 * | year            | INTEGER | NOT NULL — CRA tax year, e.g. 2026             |
 * | vehicle_id      | INTEGER | Nullable FK to vehicles.id                     |
 * | generated_at    | INTEGER | NOT NULL — epoch millis of export completion    |
 * | file_uri        | TEXT    | NOT NULL — file:// URI in app cache dir         |
 * | file_size_bytes | INTEGER | NOT NULL — file size in bytes                  |
 *
 * Indices added:
 * - `year`         — supports filtering by tax year in ReportHistoryScreen
 * - `vehicle_id`   — supports per-vehicle history lookups
 * - `generated_at` — supports cleanup worker threshold queries
 */
val Migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `report_history` (
                `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `format`          TEXT    NOT NULL,
                `year`            INTEGER NOT NULL,
                `vehicle_id`      INTEGER,
                `generated_at`    INTEGER NOT NULL,
                `file_uri`        TEXT    NOT NULL,
                `file_size_bytes` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_history_year` ON `report_history` (`year`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_history_vehicle_id` ON `report_history` (`vehicle_id`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_report_history_generated_at` ON `report_history` (`generated_at`)"
        )
    }
}
