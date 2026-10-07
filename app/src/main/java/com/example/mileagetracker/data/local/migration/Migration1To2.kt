package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from database version 1 to version 2.
 *
 * The migration expands the legacy `drives` table, back-fills all required
 * Phase 1 columns, rebuilds the table with the final Room v2 schema, and then
 * creates the new Phase 1 tables introduced at version 2.
 */
val Migration1To2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        migrateDrivesTable(db)
        createPhaseOneTables(db)
    }
}

private fun migrateDrivesTable(db: SupportSQLiteDatabase) {
    addDriveColumns(db)
    backfillDriveColumns(db)
    rebuildDrivesTable(db)
    recreateDriveIndices(db)
}

private fun addDriveColumns(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE drives ADD COLUMN started_at INTEGER")
    db.execSQL("ALTER TABLE drives ADD COLUMN ended_at INTEGER")
    db.execSQL("ALTER TABLE drives ADD COLUMN date_string TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN time_string TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN start_location TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN end_location TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN distance_km REAL")
    db.execSQL("ALTER TABLE drives ADD COLUMN vehicle_id INTEGER")
    db.execSQL("ALTER TABLE drives ADD COLUMN driver_uid TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN trip_category TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
    db.execSQL("ALTER TABLE drives ADD COLUMN purpose_category_new TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN classification_status TEXT NOT NULL DEFAULT 'PENDING'")
    db.execSQL("ALTER TABLE drives ADD COLUMN purpose_text TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN classification_confidence REAL")
    db.execSQL("ALTER TABLE drives ADD COLUMN route_polyline TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN applied_rate_snapshot REAL NOT NULL DEFAULT 0.0")
    db.execSQL("ALTER TABLE drives ADD COLUMN applied_rate_new REAL")
    db.execSQL("ALTER TABLE drives ADD COLUMN reimbursement_amount_snapshot REAL")
    db.execSQL("ALTER TABLE drives ADD COLUMN notes TEXT")
    db.execSQL("ALTER TABLE drives ADD COLUMN tracking_source TEXT NOT NULL DEFAULT 'LEGACY_IMPORT'")
    db.execSQL("ALTER TABLE drives ADD COLUMN created_at INTEGER")
    db.execSQL("ALTER TABLE drives ADD COLUMN updated_at INTEGER")
    db.execSQL("ALTER TABLE drives ADD COLUMN deleted_at INTEGER")
}

private fun backfillDriveColumns(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        UPDATE drives SET
            started_at                = timestamp,
            ended_at                  = NULL,
            date_string               = dateString,
            time_string               = timeString,
            start_location            = startLocation,
            end_location              = endLocation,
            distance_km               = distanceKm,
            vehicle_id                = NULL,
            driver_uid                = NULL,
            trip_category             = CASE purposeCategory
                                          WHEN 'Business'     THEN 'BUSINESS'
                                          WHEN 'Personal'     THEN 'PERSONAL'
                                          WHEN 'Unclassified' THEN 'UNCLASSIFIED'
                                          ELSE                     'UNCLASSIFIED'
                                        END,
            purpose_category_new      = purposeCategory,
            classification_status     = CASE purposeCategory
                                          WHEN 'Unclassified' THEN 'PENDING'
                                          ELSE                     'USER_CONFIRMED'
                                        END,
            purpose_text              = NULL,
            classification_confidence = NULL,
            route_polyline            = NULL,
            applied_rate_snapshot     = COALESCE(appliedRate, 0.0),
            applied_rate_new          = appliedRate,
            reimbursement_amount_snapshot = NULL,
            notes                     = NULL,
            tracking_source           = 'LEGACY_IMPORT',
            created_at                = timestamp,
            updated_at                = timestamp,
            deleted_at                = NULL
        """.trimIndent(),
    )
}

private fun rebuildDrivesTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE drives_new (
            id                            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            started_at                    INTEGER NOT NULL,
            ended_at                      INTEGER,
            date_string                   TEXT,
            time_string                   TEXT,
            timestamp                     INTEGER,
            start_location                TEXT NOT NULL,
            end_location                  TEXT NOT NULL,
            distance_km                   REAL NOT NULL,
            vehicle_id                    INTEGER,
            driver_uid                    TEXT,
            trip_category                 TEXT NOT NULL,
            purpose_category              TEXT,
            classification_status         TEXT NOT NULL,
            purpose_text                  TEXT,
            classification_confidence     REAL,
            route_polyline                TEXT,
            applied_rate_snapshot         REAL NOT NULL,
            applied_rate                  REAL,
            reimbursement_amount_snapshot REAL,
            notes                         TEXT,
            tracking_source               TEXT NOT NULL,
            created_at                    INTEGER NOT NULL,
            updated_at                    INTEGER NOT NULL,
            deleted_at                    INTEGER
        )
        """.trimIndent(),
    )

    db.execSQL(
        """
        INSERT INTO drives_new (
            id, started_at, ended_at, date_string, time_string, timestamp,
            start_location, end_location, distance_km, vehicle_id, driver_uid,
            trip_category, purpose_category, classification_status,
            purpose_text, classification_confidence, route_polyline,
            applied_rate_snapshot, applied_rate, reimbursement_amount_snapshot,
            notes, tracking_source, created_at, updated_at, deleted_at
        )
        SELECT
            id, started_at, ended_at, date_string, time_string, timestamp,
            start_location, end_location, distance_km, vehicle_id, driver_uid,
            trip_category, purpose_category_new, classification_status,
            purpose_text, classification_confidence, route_polyline,
            applied_rate_snapshot, applied_rate_new, reimbursement_amount_snapshot,
            notes, tracking_source, created_at, updated_at, deleted_at
        FROM drives
        """.trimIndent(),
    )

    db.execSQL("DROP TABLE drives")
    db.execSQL("ALTER TABLE drives_new RENAME TO drives")
}

private fun recreateDriveIndices(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE INDEX IF NOT EXISTS index_drives_vehicle_id ON drives (vehicle_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_drives_driver_uid ON drives (driver_uid)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_drives_started_at ON drives (started_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_drives_classification_status ON drives (classification_status)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_drives_deleted_at ON drives (deleted_at)")
}

private fun createPhaseOneTables(db: SupportSQLiteDatabase) {
    createVehiclesTable(db)
    createTripAuditTable(db)
    createCraRatesTable(db)
    createTripAttachmentsTable(db)
}

private fun createVehiclesTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS vehicles (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            owner_uid           TEXT NOT NULL,
            display_name        TEXT NOT NULL,
            make                TEXT,
            model               TEXT,
            year                INTEGER,
            ownership_type      TEXT NOT NULL,
            vehicle_type        TEXT,
            is_active           INTEGER NOT NULL DEFAULT 1,
            is_default          INTEGER NOT NULL DEFAULT 0,
            fiscal_year         INTEGER NOT NULL,
            opening_odometer_km REAL,
            closing_odometer_km REAL,
            created_at          INTEGER NOT NULL,
            updated_at          INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_owner_uid ON vehicles (owner_uid)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_vehicles_owner_uid_fiscal_year ON vehicles (owner_uid, fiscal_year)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_is_active ON vehicles (is_active)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_vehicles_is_default ON vehicles (is_default)")
}

private fun createTripAuditTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS trip_audit (
            id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            trip_id     INTEGER NOT NULL,
            actor_uid   TEXT NOT NULL,
            event_type  TEXT NOT NULL,
            field_name  TEXT,
            old_value   TEXT,
            new_value   TEXT,
            changed_at  INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_audit_trip_id ON trip_audit (trip_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_audit_actor_uid ON trip_audit (actor_uid)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_audit_changed_at ON trip_audit (changed_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_audit_event_type ON trip_audit (event_type)")
}

private fun createCraRatesTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS cra_rates (
            id                        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            tax_year                  INTEGER NOT NULL,
            first_tier_rate_cents     INTEGER NOT NULL,
            first_tier_limit_km       INTEGER NOT NULL,
            additional_km_rate_cents  INTEGER NOT NULL,
            source_label              TEXT NOT NULL,
            effective_from            INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_cra_rates_tax_year ON cra_rates (tax_year)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS index_cra_rates_tax_year_effective_from ON cra_rates (tax_year, effective_from)",
    )
}

private fun createTripAttachmentsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS trip_attachments (
            id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            trip_id     INTEGER NOT NULL,
            uri         TEXT NOT NULL,
            mime_type   TEXT NOT NULL,
            file_name   TEXT NOT NULL,
            created_at  INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_attachments_trip_id ON trip_attachments (trip_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_attachments_created_at ON trip_attachments (created_at)")
}
