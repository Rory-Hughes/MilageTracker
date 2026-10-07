package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single immutable audit log entry for a drive trip.
 *
 * A new row is inserted (never updated) each time a [DriveEntity] is created
 * or any of its auditable fields change.  This gives a full, append-only
 * change history that satisfies CRA record-keeping expectations and supports
 * the "undo last change" UX pattern.
 *
 * ## Immutability contract
 * Rows in this table must **never** be updated after insert.  Deletions are
 * only permitted if the parent drive is hard-deleted (cascade) or as part of
 * a GDPR-style data-erasure request.  All DAOs for this table should expose
 * insert and read operations only.
 *
 * ## Field encoding
 * [oldValue] and [newValue] are stored as plain strings.  Callers are
 * responsible for serialising typed values to a human-readable form before
 * writing (e.g. `TripCategory.BUSINESS.name`, `"42.3"` for a Float).  This
 * keeps the table schema stable across future type changes and avoids the
 * need for per-field type converters in the audit layer.
 *
 * ## Event types with no field change
 * For [AuditEventType.DRIVE_CREATED] the [fieldName], [oldValue], and
 * [newValue] columns will all be `null` — the event documents existence, not
 * a diff.
 */
@Entity(
    tableName = "trip_audit",
    indices = [
        Index("trip_id"),
        Index("actor_uid"),
        Index("changed_at"),
        Index("event_type"),
    ],
)
data class TripAuditEntity(

    // ── Identity ──────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Trip reference ────────────────────────────────────────────────────────

    /**
     * The [DriveEntity.id] this audit row belongs to.
     *
     * Note: @ForeignKey is intentionally omitted here for the same reason as
     * on [DriveEntity.vehicleId] — it will be added in Task 6.1 once all
     * entities are registered together in [MileageDatabase] and a migration
     * exists.  Referential integrity is enforced by [RecordTripAuditUseCase].
     */
    @ColumnInfo(name = "trip_id")
    val tripId: Int,

    // ── Actor ────────────────────────────────────────────────────────────────

    /**
     * Firebase UID (or local user ID) of the user who triggered the change.
     * For system-initiated events (e.g. auto-classification by a rule engine)
     * use the sentinel value `"SYSTEM"`.
     */
    @ColumnInfo(name = "actor_uid")
    val actorUid: String,

    // ── Event ────────────────────────────────────────────────────────────────

    /**
     * The kind of change that occurred.
     * Stored as the enum [name] string via a Room @TypeConverter.
     *
     * @see AuditEventType
     */
    @ColumnInfo(name = "event_type")
    val eventType: AuditEventType,

    // ── Diff ──────────────────────────────────────────────────────────────────

    /**
     * The name of the [DriveEntity] field that was changed, using the
     * canonical snake_case column name (e.g. `"trip_category"`,
     * `"distance_km"`, `"vehicle_id"`).
     *
     * Null for [AuditEventType.DRIVE_CREATED] and any event that does not
     * correspond to a single-field change.
     */
    @ColumnInfo(name = "field_name")
    val fieldName: String? = null,

    /**
     * String representation of the field’s value before the change.
     *
     * Null when there was no prior value (e.g. first-time assignment) or
     * for [AuditEventType.DRIVE_CREATED].
     *
     * Encoding examples:
     * - `TripCategory` → `"UNCLASSIFIED"`
     * - `Float`        → `"42.3"`
     * - `Int?`         → `"null"` or omit (use `null` here)
     */
    @ColumnInfo(name = "old_value")
    val oldValue: String? = null,

    /**
     * String representation of the field’s value after the change.
     *
     * Null for [AuditEventType.DRIVE_CREATED] (no meaningful “new value”
     * concept for a creation event) or if the field was cleared.
     */
    @ColumnInfo(name = "new_value")
    val newValue: String? = null,

    // ── Timestamp ─────────────────────────────────────────────────────────────

    /**
     * Unix epoch millis when this audit event was recorded.
     * Always set at insert time; must never be updated.
     */
    @ColumnInfo(name = "changed_at")
    val changedAt: Long = System.currentTimeMillis(),
)
