package com.example.mileagetracker.data.local.entity

/**
 * Describes how a trip record entered the system.
 *
 * Stored as its [name] string via a Room @TypeConverter so that reordering
 * entries never causes silent data corruption.
 *
 * - [AUTO_TRACKED]  – created automatically by background tracking.
 * - [MANUAL_ENTRY]  – entered directly by the user.
 * - [MANUAL_REPAIR] – corrected or repaired by the user after the fact.
 * - [LEGACY_IMPORT] – imported from an older schema or external source.
 */
enum class TrackingSource {
    AUTO_TRACKED,
    MANUAL_ENTRY,
    MANUAL_REPAIR,
    LEGACY_IMPORT,
}
