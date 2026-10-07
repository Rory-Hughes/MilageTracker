package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration: version 6 → 7.
 *
 * ## Changes
 * - Adds table `user_classification_rules` for user-defined keyword rules
 *   (Task 4.3b).
 *
 * Column mapping:
 * | Column        | Type    | Notes                                  |
 * |---------------|---------|----------------------------------------|
 * | id            | INTEGER | PK auto-increment                      |
 * | name          | TEXT    | NOT NULL — user label                  |
 * | keyword       | TEXT    | NOT NULL — case-insensitive substring   |
 * | category      | TEXT    | NOT NULL — TripCategory.name           |
 * | confidence    | REAL    | DEFAULT 0.75                           |
 * | priority      | INTEGER | DEFAULT 50                             |
 * | is_enabled    | INTEGER | Boolean (0/1), DEFAULT 1               |
 * | created_at    | INTEGER | Epoch millis                           |
 */
val Migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_classification_rules` (
                `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name`       TEXT    NOT NULL,
                `keyword`    TEXT    NOT NULL,
                `category`   TEXT    NOT NULL,
                `confidence` REAL    NOT NULL DEFAULT 0.75,
                `priority`   INTEGER NOT NULL DEFAULT 50,
                `is_enabled` INTEGER NOT NULL DEFAULT 1,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
