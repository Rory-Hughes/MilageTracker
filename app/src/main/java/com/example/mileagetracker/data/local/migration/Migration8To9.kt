package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration: version 8 → 9.
 *
 * ## Changes
 * - Adds table `vehicle_expenses` for full vehicle expense tracking (Task 8.1).
 *
 * Column mapping:
 * | Column                  | Type    | Notes                                               |
 * |-------------------------|---------|-----------------------------------------------------|
 * | id                      | INTEGER | PK auto-increment                                   |
 * | vehicle_id              | INTEGER | NOT NULL, FK → vehicles.id CASCADE DELETE           |
 * | category_key            | TEXT    | NOT NULL — ExpenseCategory.key string               |
 * | amount_cad              | REAL    | NOT NULL — total amount in CAD (> 0)                |
 * | date                    | INTEGER | NOT NULL — epoch millis of expense date             |
 * | vendor_name             | TEXT    | Nullable — vendor / payee name                      |
 * | notes                   | TEXT    | Nullable free-form note                             |
 * | receipt_attachment_uri  | TEXT    | Nullable app-internal file:// URI of receipt image  |
 * | gst_hst_amount          | REAL    | Nullable GST/HST component                          |
 * | is_business_use         | INTEGER | NOT NULL (0/1 boolean)                              |
 * | created_at              | INTEGER | NOT NULL — row-insert epoch millis                 |
 *
 * Indices added:
 * - `vehicle_id`        — FK integrity + per-vehicle list query
 * - `vehicle_id, date`  — year-filtered per-vehicle queries
 * - `date`              — aggregate / year-range queries
 */
val Migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vehicle_expenses` (
                `id`                     INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `vehicle_id`             INTEGER NOT NULL,
                `category_key`           TEXT    NOT NULL,
                `amount_cad`             REAL    NOT NULL,
                `date`                   INTEGER NOT NULL,
                `vendor_name`            TEXT,
                `notes`                  TEXT,
                `receipt_attachment_uri` TEXT,
                `gst_hst_amount`         REAL,
                `is_business_use`        INTEGER NOT NULL DEFAULT 1,
                `created_at`             INTEGER NOT NULL,
                FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vehicle_expenses_vehicle_id` ON `vehicle_expenses` (`vehicle_id`)"
        )
        db.execSQL(
            """
                CREATE INDEX IF NOT EXISTS `index_vehicle_expenses_vehicle_id_date` 
                ON `vehicle_expenses` (`vehicle_id`, `date`)
            """

        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vehicle_expenses_date` ON `vehicle_expenses` (`date`)"
        )
    }
}
