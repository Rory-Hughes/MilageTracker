package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migration from version 4 → 5.
 *
 * No DDL changes are required between v4 and v5 — the existing migrations
 * (1→2, 2→3, 3→4) already produce a schema that matches [DriveEntity]
 * exactly. This migration exists solely to advance the version counter so
 * that Room accepts the database without triggering a hash-mismatch crash.
 *
 * If Room still reports a mismatch after this migration runs, the database
 * builder falls back to destructive recreation (see [MileageDatabase]).
 * This is safe during development; replace with explicit ALTER statements
 * before shipping to production.
 */
val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No-op: schema is already correct after migrations 1→2, 2→3, 3→4.
    }
}
