package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [OdometerCaptureEntity] (table: `odometer_captures`).
 *
 * ## Query notes
 * - [getForVehicle] returns a reactive [Flow] sorted newest-first so the
 *   UI history list stays automatically up to date.
 * - [getLatestForVehicle] is a one-shot suspend function — used by
 *   [SaveOdometerCaptureUseCase] to validate that the new reading is ≥
 *   the previous one.
 * - [getFirstCaptureForVehicleInYear] / [getLastCaptureForVehicleInYear]
 *   are one-shot functions added in Task 5.2 to support annual odometer
 *   open/close readings for the per-vehicle annual summary.
 */
@Dao
interface OdometerCaptureDao {

    /**
     * Inserts a new capture row.  [OnConflictStrategy.REPLACE] means that if
     * a row with the same [OdometerCaptureEntity.id] already exists (e.g. an
     * edit flow), it is atomically replaced.
     *
     * @return The row-id of the inserted (or replaced) row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: OdometerCaptureEntity): Long

    /**
     * Reactive stream of all captures for [vehicleId], ordered by
     * [OdometerCaptureEntity.captureDate] descending (newest first).
     */
    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
        ORDER BY capture_date DESC
        """
    )
    fun getForVehicle(vehicleId: Int): Flow<List<OdometerCaptureEntity>>

    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
        ORDER BY capture_date DESC
        """
    )
    suspend fun getForVehicleOnce(vehicleId: Int): List<OdometerCaptureEntity>

    @Query("DELETE FROM odometer_captures WHERE vehicle_id = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: Int)

    /**
     * Returns the single most-recent capture for [vehicleId], or `null` if
     * no captures exist yet for that vehicle.
     *
     * Used by [SaveOdometerCaptureUseCase] to determine whether the incoming
     * reading is a regression (lower than the last recorded value).
     */
    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
        ORDER BY capture_date DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForVehicle(vehicleId: Int): OdometerCaptureEntity?

    // ── Task 5.2 — annual boundary queries ────────────────────────────────────

    /**
     * Returns the earliest odometer capture for [vehicleId] within the
     * calendar year defined by [[fromMillis], [toMillis]] (epoch millis).
     *
     * This is the "opening odometer" for the annual summary — the first
     * reading recorded at or after January 1 of the tax year.
     *
     * Returns `null` if no captures exist for the vehicle in that window.
     *
     * @param vehicleId  the vehicle to query.
     * @param fromMillis start of the year, inclusive (Jan 1 00:00:00 UTC).
     * @param toMillis   end of the year, inclusive (Dec 31 23:59:59 UTC).
     */
    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id   = :vehicleId
          AND capture_date >= :fromMillis
          AND capture_date <= :toMillis
        ORDER BY capture_date ASC
        LIMIT 1
        """
    )
    suspend fun getFirstCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCaptureEntity?

    /**
     * Returns the latest odometer capture for [vehicleId] within the
     * calendar year defined by [[fromMillis], [toMillis]] (epoch millis).
     *
     * This is the "closing odometer" for the annual summary — the last
     * reading recorded on or before December 31 of the tax year.
     *
     * Returns `null` if no captures exist for the vehicle in that window.
     *
     * @param vehicleId  the vehicle to query.
     * @param fromMillis start of the year, inclusive (Jan 1 00:00:00 UTC).
     * @param toMillis   end of the year, inclusive (Dec 31 23:59:59 UTC).
     */
    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id   = :vehicleId
          AND capture_date >= :fromMillis
          AND capture_date <= :toMillis
        ORDER BY capture_date DESC
        LIMIT 1
        """
    )
    suspend fun getLastCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCaptureEntity?

    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
          AND tax_year = :taxYear
          AND boundary_type = 'OPENING'
        ORDER BY capture_date ASC
        LIMIT 1
        """
    )
    suspend fun getOpeningCaptureForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
    ): OdometerCaptureEntity?

    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
          AND tax_year = :taxYear
          AND boundary_type = 'CLOSING'
        ORDER BY capture_date DESC
        LIMIT 1
        """
    )
    suspend fun getClosingCaptureForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
    ): OdometerCaptureEntity?

    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
          AND tax_year = :taxYear
          AND boundary_type = 'OTHER'
          AND capture_date >= :fromMillis
          AND capture_date <= :toMillis
        ORDER BY capture_date ASC
        LIMIT 1
        """
    )
    suspend fun getFirstOtherCaptureForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCaptureEntity?

    @Query(
        """
        SELECT * FROM odometer_captures
        WHERE vehicle_id = :vehicleId
          AND tax_year = :taxYear
          AND boundary_type = 'OTHER'
          AND capture_date >= :fromMillis
          AND capture_date <= :toMillis
        ORDER BY capture_date DESC
        LIMIT 1
        """
    )
    suspend fun getLastOtherCaptureForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCaptureEntity?
}
