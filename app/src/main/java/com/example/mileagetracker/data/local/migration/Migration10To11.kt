package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 10 → 11.
 *
 * Adds the `policy_templates` table introduced by Task 8.4.
 */
val Migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `policy_templates` (
                `id`                      TEXT NOT NULL,
                `name`                    TEXT NOT NULL,
                `rate_mode`               TEXT NOT NULL,
                `custom_rate_cents_per_km` INTEGER,
                `eligible_categories`     TEXT NOT NULL,
                `exclude_commute`         INTEGER NOT NULL DEFAULT 1,
                `require_purpose`         INTEGER NOT NULL DEFAULT 0,
                `require_attachment`      INTEGER NOT NULL DEFAULT 0,
                `max_daily_km`            INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
