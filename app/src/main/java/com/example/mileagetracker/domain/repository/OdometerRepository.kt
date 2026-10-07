package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.OdometerCapture
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for odometer capture operations.
 *
 * ## Responsibilities
 * - Persists new captures (delegating photo file-copy to the implementation).
 * - Provides a reactive stream of captures per vehicle.
 * - Returns the latest reading for a vehicle (used by the use-case validation
 *   layer to detect odometer regressions).
 * - Returns year-bounded first/last captures for annual odometer open/close
 *   readings (Task 5.2).
 *
 * ## Implementation
 * [OdometerRepositoryImpl] satisfies this interface and is bound in
 * [RepositoryModule].
 */
interface OdometerRepository {

    /**
     * Persists [capture] and, if [capture.photoUri] refers to a camera temp
     * URI, copies the photo into app-internal storage before saving the
     * internal URI on the entity.
     *
     * @return The saved [OdometerCapture] with its database-assigned [OdometerCapture.id]
     *         and updated [OdometerCapture.photoUri] (pointing to internal storage).
     */
    suspend fun saveCapture(capture: OdometerCapture): OdometerCapture

    /**
     * Reactive stream of all captures for [vehicleId], sorted newest-first.
     * Emits a new list every time the underlying table changes.
     */
    fun observeCapturesForVehicle(vehicleId: Int): Flow<List<OdometerCapture>>

    /**
     * Returns the most recent [OdometerCapture] for [vehicleId], or `null`
     * if no captures have been recorded yet for that vehicle.
     */
    suspend fun getLatestReading(vehicleId: Int): OdometerCapture?

    // ── Task 5.2 — year-bounded odometer queries ───────────────────────────────

    /**
     * Returns the earliest odometer capture for [vehicleId] within the
     * calendar year bounded by [[fromMillis], [toMillis]].
     *
     * This is the "opening odometer" reading for an annual summary — i.e.
     * the first photo/manual entry recorded at or after January 1 of the
     * tax year.
     *
     * Returns `null` if no captures exist for the vehicle in that window.
     *
     * @param vehicleId  the vehicle to query.
     * @param fromMillis start of the year boundary, inclusive (epoch millis).
     * @param toMillis   end of the year boundary, inclusive (epoch millis).
     */
    suspend fun getFirstCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture?

    /**
     * Returns the latest odometer capture for [vehicleId] within the
     * calendar year bounded by [[fromMillis], [toMillis]].
     *
     * This is the "closing odometer" reading — the last photo/manual entry
     * recorded on or before December 31 of the tax year.
     *
     * Returns `null` if no captures exist for the vehicle in that window.
     *
     * @param vehicleId  the vehicle to query.
     * @param fromMillis start of the year boundary, inclusive (epoch millis).
     * @param toMillis   end of the year boundary, inclusive (epoch millis).
     */
    suspend fun getLastCaptureForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture?

    suspend fun getOpeningEvidenceForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture?

    suspend fun getClosingEvidenceForVehicleYear(
        vehicleId: Int,
        taxYear: Int,
        fromMillis: Long,
        toMillis: Long,
    ): OdometerCapture?
}
