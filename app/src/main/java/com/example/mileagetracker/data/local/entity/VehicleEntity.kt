package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a vehicle that can be associated with one or more drive trips.
 *
 * ## Design notes
 *
 * - A user may own or use multiple vehicles.  Only one vehicle per
 *   ([ownerUid], [fiscalYear]) pair may have [isDefault] = `true`.
 *   This constraint is enforced by [VehicleValidator] in the domain layer
 *   rather than by a SQL unique index, to produce a readable error message.
 *
 * - Odometer values are per-vehicle per-fiscal-year.  When a new fiscal year
 *   begins, a new row is created and [openingOdometerKm] is set to the prior
 *   year's [closingOdometerKm].  The old row is kept for audit purposes.
 *
 * - Deactivating a vehicle sets [isActive] = `false`.  It does NOT delete the
 *   row; existing [DriveEntity] rows that reference this vehicle remain linked
 *   so that historical reports stay intact.
 *
 * - [vehicleType] is stored as a free-form string (e.g. `"Sedan"`, `"SUV"`,
 *   `"Pickup"`, `"Motorcycle"`) rather than an enum to allow user-defined
 *   types without a schema migration.
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index("owner_uid"),
        Index("owner_uid", "fiscal_year"),
        Index("is_active"),
        Index("is_default"),
    ],
)
data class VehicleEntity(

    // ── Identity ──────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Owner ─────────────────────────────────────────────────────────────────

    /**
     * Firebase UID (or local user ID) of the user who owns/manages this
     * vehicle record.  All queries should scope by [ownerUid] to support
     * future multi-user scenarios.
     */
    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Human-readable label shown in the UI, e.g. "My Civic" or
     * "Company Ford F-150".  Must not be blank; enforced by [VehicleValidator].
     */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    // ── Vehicle details ───────────────────────────────────────────────────────

    /**
     * Manufacturer name, e.g. "Honda", "Ford", "Toyota".
     * Optional; displayed alongside [model] in vehicle selection lists.
     */
    @ColumnInfo(name = "make")
    val make: String? = null,

    /**
     * Model name, e.g. "Civic", "F-150", "Corolla".
     * Optional; displayed alongside [make] in vehicle selection lists.
     */
    @ColumnInfo(name = "model")
    val model: String? = null,

    /**
     * Model year as a four-digit integer, e.g. `2021`.
     * Optional; used for display only.  Validated by [VehicleValidator] to
     * be within a reasonable range (1900 – current year + 1).
     */
    @ColumnInfo(name = "year")
    val year: Int? = null,

    /**
     * How the vehicle is owned.  Drives the reimbursement rules available
     * for this vehicle (CRA personal-vehicle rates vs company-car rules).
     *
     * Stored as the enum [name] string via a Room @TypeConverter.
     */
    @ColumnInfo(name = "ownership_type")
    val ownershipType: VehicleOwnershipType = VehicleOwnershipType.PERSONAL,

    /**
     * Free-form vehicle type label, e.g. `"Sedan"`, `"SUV"`, `"Pickup"`,
     * `"Motorcycle"`, `"Van"`.  Stored as a plain string to allow user-defined
     * values without requiring a schema migration.
     */
    @ColumnInfo(name = "vehicle_type")
    val vehicleType: String? = null,

    // ── Status / flags ────────────────────────────────────────────────────────

    /**
     * Whether this vehicle is currently active and available for trip
     * assignment.  Set to `false` to archive/deactivate without deleting.
     * Inactive vehicles are hidden from selection lists but retained for
     * historical report accuracy.
     */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /**
     * Whether this is the default vehicle for the [ownerUid] in [fiscalYear].
     * At most one vehicle per ([ownerUid], [fiscalYear]) pair may be `true`.
     * New trips are pre-assigned to the default vehicle when one exists.
     */
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    // ── Fiscal year ───────────────────────────────────────────────────────────

    /**
     * The Canadian tax/fiscal year this odometer record applies to,
     * e.g. `2026`.  One [VehicleEntity] row exists per vehicle per fiscal year
     * so that opening/closing odometer values are scoped correctly.
     */
    @ColumnInfo(name = "fiscal_year")
    val fiscalYear: Int,

    // ── Odometer ──────────────────────────────────────────────────────────────

    /**
     * Odometer reading in kilometres at the start of [fiscalYear].
     * Required for CRA compliance (total annual distance = closing − opening).
     * Null if the vehicle was added mid-year and the opening reading is unknown.
     */
    @ColumnInfo(name = "opening_odometer_km")
    val openingOdometerKm: Float? = null,

    /**
     * Odometer reading in kilometres at the end of [fiscalYear], or the
     * most recent known reading if the year is still in progress.
     * Updated by [UpdateVehicleOdometerUseCase] as trips are recorded.
     * Null until the first update is applied.
     */
    @ColumnInfo(name = "closing_odometer_km")
    val closingOdometerKm: Float? = null,

    // ── Audit timestamps ──────────────────────────────────────────────────────

    /** Unix epoch millis when this row was first inserted. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Unix epoch millis of the most recent update to any field on this row. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
