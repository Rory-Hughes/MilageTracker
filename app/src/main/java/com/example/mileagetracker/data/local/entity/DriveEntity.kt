package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single recorded drive trip.
 *
 * ## Phase 1 changes (Task 4.1)
 * The entity has been expanded from a basic trip log to a fully auditable,
 * CRA-ready record. Key additions:
 *
 * - **Canonical timestamps** – [startedAt] / [endedAt] replace the old
 *   [timestamp] epoch millis and the display-only [dateString]/[timeString]
 *   helpers.  All three legacy fields are retained as nullable columns so
 *   that the Room migration can back-fill them without data loss.
 * - **Vehicle reference** – [vehicleId] is a nullable FK to the `vehicles`
 *   table added in Task 4.2.  The @ForeignKey annotation is intentionally
 *   omitted here; it will be declared once VehicleEntity is registered in
 *   MileageDatabase and a proper Room migration exists (Task 6.x).  The
 *   index on `vehicle_id` is retained for query performance.
 * - **Driver UID** – [driverUid] ties the drive to a Firebase or local user.
 * - **Purpose text** – [purposeText] stores a free-form description used when
 *   [tripCategory] is [TripCategory.CUSTOM] or when the user annotates any
 *   business trip for CRA log purposes.
 * - **Classification status** – [classificationStatus] replaces the legacy
 *   hard-coded string `"Unclassified"` / `"Business"` / `"Personal"` stored in
 *   [purposeCategory].  The old [purposeCategory] column is retained as a
 *   nullable String for migration compatibility only; all new code should read
 *   [tripCategory] and [classificationStatus].
 * - **Route geometry** – [routePolyline] stores the encoded polyline (Google
 *   format) or a JSON array of lat/lng pairs captured by the tracking service.
 * - **Confidence score** – [classificationConfidence] is a 0–1 Float set when
 *   the trip was auto-classified by a rule engine or ML model.  Null for
 *   manually entered or legacy trips.
 * - **Notes** – [notes] is a free-form text annotation added by the user.
 * - **Reimbursement snapshot** – [appliedRateSnapshot] freezes the per-km CRA
 *   rate at the time of classification (supersedes [appliedRate] which is kept
 *   as an alias for migration).  [reimbursementAmountSnapshot] caches the
 *   computed dollar value so reports don't need to recalculate on the fly.
 * - **Tracking source** – [trackingSource] records how the trip was captured
 *   ([TrackingSource]).
 * - **Audit timestamps** – [createdAt] and [updatedAt] support change tracking.
 * - **Soft delete** – [deletedAt] is null for live records; a non-null epoch
 *   millis value means the drive has been logically deleted and should be
 *   excluded from all active queries via `WHERE deleted_at IS NULL`.
 *
 * ## Task 2.1 additions
 * - **Tracking metadata** – [trackingMetadata] embeds five `meta_*` columns
 *   capturing GPS/battery provenance for CRA audit trails.
 *   See [TrackingMetadataEmbedded].
 *
 * ## FK note
 * The foreign-key relationship to `vehicles` is enforced at the application
 * layer by [AssignVehicleToDriveUseCase].  The Room @ForeignKey will be added
 * in a future task once [VehicleEntity] is wired into [MileageDatabase].
 */
@Entity(
    tableName = "drives",
    indices = [
        Index("vehicle_id"),
        Index("driver_uid"),
        Index("started_at"),
        Index("classification_status"),
        Index("deleted_at"),
    ],
)
data class DriveEntity(

    // ── Identity ─────────────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Canonical timestamps ──────────────────────────────────────────────────────────

    /** Unix epoch millis when the trip started.  Replaces the legacy [timestamp] field. */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    /** Unix epoch millis when the trip ended.  Null if the trip is still in progress. */
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    // ── Legacy display helpers (retained for migration; nullable) ─────────────────

    /**
     * Pre-formatted date string, e.g. "May 17, 2026".
     *
     * **Deprecated** – kept as a nullable column so that existing rows survive
     * the migration without data loss.  New code should derive display strings
     * from [startedAt] using a formatter in the UI/domain layer.
     */
    @ColumnInfo(name = "date_string")
    val dateString: String? = null,

    /**
     * Pre-formatted time string, e.g. "07:45 AM".
     *
     * **Deprecated** – same deprecation note as [dateString].
     */
    @ColumnInfo(name = "time_string")
    val timeString: String? = null,

    // ── Legacy sort timestamp (retained for migration) ────────────────────────────

    /**
     * Original epoch millis sort column.
     *
     * **Deprecated** – back-filled from [startedAt] during migration.  All
     * new DAO queries should `ORDER BY started_at DESC` instead.
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long? = null,

    // ── Location ─────────────────────────────────────────────────────────────────────

    /** Human-readable start address or "lat,lng" fallback. */
    @ColumnInfo(name = "start_location")
    val startLocation: String,

    /** Human-readable end address or "lat,lng" fallback. */
    @ColumnInfo(name = "end_location")
    val endLocation: String,

    // ── Distance ─────────────────────────────────────────────────────────────────────

    /** Trip distance in kilometres.  Central to all CRA financial calculations. */
    @ColumnInfo(name = "distance_km")
    val distanceKm: Float,

    // ── Vehicle reference ────────────────────────────────────────────────────────────

    /**
     * References `vehicles.id`.  Null until the driver assigns a vehicle.
     * Migration back-fill: `NULL`.
     *
     * Note: @ForeignKey is omitted until [VehicleEntity] is registered in
     * [MileageDatabase] (Task 6.1).  Referential integrity is enforced by
     * [AssignVehicleToDriveUseCase] in the meantime.
     */
    @ColumnInfo(name = "vehicle_id")
    val vehicleId: Int? = null,

    // ── Driver ─────────────────────────────────────────────────────────────────────────

    /**
     * Firebase UID (or local user ID) of the driver who recorded this trip.
     * Migration back-fill: placeholder string `"LEGACY_USER"` or the current
     * authenticated UID if available.
     */
    @ColumnInfo(name = "driver_uid")
    val driverUid: String? = null,

    // ── Classification ────────────────────────────────────────────────────────────────

    /**
     * Canonical trip category stored as the enum [name] string.
     * Replaces the legacy [purposeCategory] raw-string discriminator.
     * Migration back-fill: map `"Unclassified"` → [TripCategory.UNCLASSIFIED],
     * `"Business"` → [TripCategory.BUSINESS], `"Personal"` → [TripCategory.PERSONAL].
     */
    @ColumnInfo(name = "trip_category")
    val tripCategory: TripCategory = TripCategory.UNCLASSIFIED,

    /**
     * Legacy raw-string category field.
     *
     * **Deprecated** – kept nullable so that existing DAO queries that filter
     * on `purposeCategory = 'Unclassified'` continue to work during the
     * migration window.  All new DAO queries must use [tripCategory].
     */
    @ColumnInfo(name = "purpose_category")
    val purposeCategory: String? = null,

    /**
     * Lifecycle stage of the classification decision.
     * Migration back-fill:
     * - old `"Unclassified"` rows → [ClassificationStatus.PENDING]
     * - old `"Business"` / `"Personal"` rows → [ClassificationStatus.USER_CONFIRMED]
     */
    @ColumnInfo(name = "classification_status")
    val classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,

    /**
     * Free-form purpose description.  Required when [tripCategory] is
     * [TripCategory.CUSTOM]; optional annotation for any other category.
     * CRA recommends a brief purpose note for each business trip.
     */
    @ColumnInfo(name = "purpose_text")
    val purposeText: String? = null,

    /**
     * 0.0–1.0 confidence score assigned by an auto-classifier.
     * Null for manually entered or legacy trips.
     */
    @ColumnInfo(name = "classification_confidence")
    val classificationConfidence: Float? = null,

    // ── Route geometry ────────────────────────────────────────────────────────────────

    /**
     * Encoded route geometry.  Stored as a Google-encoded polyline string or
     * a compact JSON array of `[lat, lng]` pairs captured by the tracking
     * service.  Null for manually entered trips or legacy rows.
     * Migration back-fill: `NULL`.
     */
    @ColumnInfo(name = "route_polyline")
    val routePolyline: String? = null,

    // ── Reimbursement snapshot ──────────────────────────────────────────────────────────

    /**
     * The CRA per-km rate frozen at the moment this trip was classified.
     * Supersedes the legacy [appliedRate] field which is retained as a
     * nullable column for migration compatibility only.
     */
    @ColumnInfo(name = "applied_rate_snapshot")
    val appliedRateSnapshot: Float = 0.0f,

    /**
     * Legacy applied rate column.
     *
     * **Deprecated** – kept as a nullable column; migration reads its value
     * into [appliedRateSnapshot].  New code writes [appliedRateSnapshot] only.
     */
    @ColumnInfo(name = "applied_rate")
    val appliedRate: Float? = null,

    /**
     * Pre-computed reimbursement amount in CAD for this trip
     * (`distanceKm * appliedRateSnapshot`).  Cached here so that aggregate
     * report queries do not need to recompute on every read.
     * Null until the trip is classified.
     */
    @ColumnInfo(name = "reimbursement_amount_snapshot")
    val reimbursementAmountSnapshot: Float? = null,

    // ── Notes ───────────────────────────────────────────────────────────────────────────

    /** Free-form user annotation, e.g. client name, job number, or memo. */
    @ColumnInfo(name = "notes")
    val notes: String? = null,

    // ── Tracking source ───────────────────────────────────────────────────────────────

    /**
     * How this trip record was created.
     * Migration back-fill: [TrackingSource.LEGACY_IMPORT].
     */
    @ColumnInfo(name = "tracking_source")
    val trackingSource: TrackingSource = TrackingSource.LEGACY_IMPORT,

    // ── Audit timestamps ─────────────────────────────────────────────────────────────

    /** Unix epoch millis when this row was first inserted. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Unix epoch millis of the most recent update to any field on this row. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    // ── Soft delete ───────────────────────────────────────────────────────────────────

    /**
     * Unix epoch millis when this drive was logically deleted, or `null` if
     * the record is still active.  All active-record queries must include
     * `WHERE deleted_at IS NULL` to exclude soft-deleted rows.
     */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    // ── Tracking metadata (Task 2.1) ─────────────────────────────────────────────

    /**
     * Embedded GPS/battery provenance snapshot populated by [DriveTrackingService]
     * at trip end. Null columns for rows created before Task 2.1 (legacy/manual).
     *
     * All five columns land in the `drives` table with the prefix `meta_`.
     * See [TrackingMetadataEmbedded] for the column definitions.
     */
    @Embedded(prefix = "meta_")
    val trackingMetadata: TrackingMetadataEmbedded? = null,
)
