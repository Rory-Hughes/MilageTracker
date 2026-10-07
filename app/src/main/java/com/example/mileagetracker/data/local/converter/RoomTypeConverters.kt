package com.example.mileagetracker.data.local.converter

import androidx.room.TypeConverter
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType

/**
 * Room type converters for all Phase 1 enum fields.
 *
 * Registered at the database level via `@TypeConverters(RoomTypeConverters::class)`
 * on [MileageDatabase] so that every DAO in the database automatically has
 * access to every converter without needing per-DAO or per-entity annotations.
 *
 * ## Storage strategy: name strings, not ordinals
 * Every enum is persisted as its [Enum.name] string (e.g. `"BUSINESS"`,
 * `"PENDING"`).  **Never** use ordinal integers for persistence because:
 * - Adding or reordering enum entries changes all ordinal values silently.
 * - A `name` string is human-readable in SQLite tooling and export files.
 * - Room's `@TypeConverter` is called transparently, so all DAO queries
 *   that compare against a string literal (e.g. `trip_category = 'BUSINESS'`)
 *   work correctly without any extra mapping.
 *
 * ## Null safety
 * All converters are null-safe in both directions:
 * - `fromString` returns `null` when the stored value is `null`.
 * - `toString` returns `null` when the enum value is `null`.
 * This is required because several entity fields are declared nullable
 * (e.g. [DriveEntity.tripCategory] defaults are non-null, but legacy rows
 * populated before Phase 1 may have a null [DriveEntity.purposeCategory]).
 *
 * ## No JSON / list converters needed for Phase 1
 * [DriveEntity.routePolyline] is already a pre-serialised `String` (encoded
 * polyline or JSON array produced by the tracking service), so Room stores
 * it as-is without a converter.  If a future phase introduces a typed
 * `List<LatLng>` field, a JSON converter should be added here.
 */
class RoomTypeConverters {

    // ───────────────────────────────────────────────────────────────────────────
    // TripCategory
    // ───────────────────────────────────────────────────────────────────────────

    @TypeConverter
    fun tripCategoryToString(value: TripCategory?): String? = value?.name

    /**
     * Converts a stored string back to [TripCategory].
     *
     * Uses [enumValueOf] which throws [IllegalArgumentException] if the stored
     * string does not match any enum entry.  This is intentional: a stored
     * value that can’t be deserialised indicates data corruption and should
     * surface as an exception rather than silently defaulting.
     */
    @TypeConverter
    fun stringToTripCategory(value: String?): TripCategory? =
        value?.let { enumValueOf<TripCategory>(it) }

    // ───────────────────────────────────────────────────────────────────────────
    // ClassificationStatus
    // ───────────────────────────────────────────────────────────────────────────

    @TypeConverter
    fun classificationStatusToString(value: ClassificationStatus?): String? = value?.name

    @TypeConverter
    fun stringToClassificationStatus(value: String?): ClassificationStatus? =
        value?.let { enumValueOf<ClassificationStatus>(it) }

    // ───────────────────────────────────────────────────────────────────────────
    // TrackingSource
    // ───────────────────────────────────────────────────────────────────────────

    @TypeConverter
    fun trackingSourceToString(value: TrackingSource?): String? = value?.name

    @TypeConverter
    fun stringToTrackingSource(value: String?): TrackingSource? =
        value?.let { enumValueOf<TrackingSource>(it) }

    // ───────────────────────────────────────────────────────────────────────────
    // VehicleOwnershipType
    // ───────────────────────────────────────────────────────────────────────────

    @TypeConverter
    fun vehicleOwnershipTypeToString(value: VehicleOwnershipType?): String? = value?.name

    @TypeConverter
    fun stringToVehicleOwnershipType(value: String?): VehicleOwnershipType? =
        value?.let { enumValueOf<VehicleOwnershipType>(it) }

    // ───────────────────────────────────────────────────────────────────────────
    // AuditEventType
    // ───────────────────────────────────────────────────────────────────────────

    @TypeConverter
    fun auditEventTypeToString(value: AuditEventType?): String? = value?.name

    @TypeConverter
    fun stringToAuditEventType(value: String?): AuditEventType? =
        value?.let { enumValueOf<AuditEventType>(it) }
}
