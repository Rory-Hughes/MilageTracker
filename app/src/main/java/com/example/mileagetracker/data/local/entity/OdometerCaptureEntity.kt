package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCaptureType

/**
 * Room entity for a single odometer reading.
 *
 * ## Table: `odometer_captures`
 *
 * ### Foreign key
 * [vehicleId] references `vehicles(id)` with `CASCADE` delete so that
 * archiving a vehicle row (which soft-deletes it via [isActive]) does NOT
 * drop its odometer history.  Hard-deleting a vehicle row (rare / admin
 * only) will cascade-delete its captures.
 *
 * ### Indexes
 * - `vehicle_id, capture_date DESC` — supports the most common query
 *   (history for a vehicle sorted newest-first).
 * - `vehicle_id` — supports FK integrity checks.
 *
 * @param id           Auto-generated PK.
 * @param vehicleId    FK to [VehicleEntity.id].
 * @param captureDate  Unix epoch millis of the calendar date the reading was taken.
 * @param readingKm    Odometer in whole kilometres.
 * @param photoUri     App-internal content URI of the odometer photo, or null.
 * @param captureType  [OdometerCaptureType] name stored as a string.
 * @param notes        Optional free-form note.
 * @param createdAt    Row-insert timestamp (epoch millis).
 */
@Entity(
    tableName = "odometer_captures",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("vehicle_id"),
        Index("vehicle_id", "capture_date"),
        Index("vehicle_id", "tax_year", "boundary_type"),
    ],
)
data class OdometerCaptureEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "vehicle_id")
    val vehicleId: Int,

    @ColumnInfo(name = "capture_date")
    val captureDate: Long,

    @ColumnInfo(name = "reading_km")
    val readingKm: Int,

    @ColumnInfo(name = "tax_year", defaultValue = "0")
    val taxYear: Int = 0,

    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,

    /** Stored as [OdometerCaptureType.name] string. */
    @ColumnInfo(name = "capture_type")
    val captureType: String = OdometerCaptureType.MANUAL.name,

    /** Stored as [OdometerBoundaryType.name] string. */
    @ColumnInfo(name = "boundary_type", defaultValue = "OTHER")
    val boundaryType: String = OdometerBoundaryType.OTHER.name,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
