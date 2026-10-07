package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 9 → 10  (Task 8.3 — Approval Queues + Period Locking)
 *
 * Adds:
 *  - `approval_requests` table
 *  - `period_locks` table
 */
val Migration9To10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `approval_requests` (
                `id`                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tripId`            INTEGER,
                `expenseId`         INTEGER,
                `submittedByUserId` INTEGER NOT NULL,
                `submittedAt`       INTEGER NOT NULL,
                `status`            TEXT    NOT NULL,
                `reviewedByUserId`  INTEGER,
                `reviewedAt`        INTEGER,
                `reviewComment`     TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `period_locks` (
                `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `periodYear`      INTEGER NOT NULL,
                `periodMonth`     INTEGER,
                `lockedByUserId`  INTEGER NOT NULL,
                `lockedAt`        INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_period_locks_year_month` ON `period_locks` (`periodYear`, `periodMonth`)"
        )
    }
}
