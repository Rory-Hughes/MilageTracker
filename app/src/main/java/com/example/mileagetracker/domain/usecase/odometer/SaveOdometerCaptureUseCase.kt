package com.example.mileagetracker.domain.usecase.odometer

import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.repository.OdometerRepository
import javax.inject.Inject

/**
 * Validates and persists a new [OdometerCapture].
 *
 * ## Validation rules
 * 1. [OdometerCapture.readingKm] must be > 0 — negative / zero odometer
 *    readings are never valid.
 * 2. If a previous capture exists for the same vehicle, the new reading
 *    should be ≥ the previous one.  A regression triggers a
 *    [OdometerSaveResult.RegressionWarning] rather than an error so the
 *    caller can surface a non-blocking warning and still persist the record
 *    (e.g. after an odometer replacement).
 *
 * ## Photo handling
 * Photo file-copy from camera temp URI to internal storage is delegated
 * entirely to [OdometerRepository.saveCapture] — this use-case is
 * intentionally side-effect-free with respect to the file system.
 *
 * ## Return type
 * Returns a sealed [OdometerSaveResult] instead of throwing so that the
 * ViewModel can present warnings without try/catch boilerplate.
 */
class SaveOdometerCaptureUseCase @Inject constructor(
    private val repo: OdometerRepository,
) {
    suspend operator fun invoke(capture: OdometerCapture): OdometerSaveResult {
        if (capture.readingKm <= 0) {
            return OdometerSaveResult.InvalidReading(
                "Odometer reading must be greater than zero."
            )
        }

        val latest = repo.getLatestReading(capture.vehicleId)
        val isRegression = latest != null && capture.readingKm < latest.readingKm

        val saved = repo.saveCapture(capture)

        return if (isRegression) {
            OdometerSaveResult.RegressionWarning(
                saved = saved,
                previous = latest!!.readingKm,
            )
        } else {
            OdometerSaveResult.Success(saved)
        }
    }
}

// ── Result types ──────────────────────────────────────────────────────────────

/**
 * Sealed result returned by [SaveOdometerCaptureUseCase].
 *
 * | Variant              | Meaning                                                      |
 * |----------------------|--------------------------------------------------------------|
 * | [Success]            | Reading saved; no anomalies detected.                        |
 * | [RegressionWarning]  | Reading saved but is lower than the previous reading.        |
 * |                      | Surface a non-blocking warning in the UI (odometer replaced?).|
 * | [InvalidReading]     | Validation failed; nothing was written to the database.      |
 */
sealed interface OdometerSaveResult {
    data class Success(val capture: OdometerCapture) : OdometerSaveResult

    data class RegressionWarning(
        val saved: OdometerCapture,
        val previous: Int,
    ) : OdometerSaveResult

    data class InvalidReading(val reason: String) : OdometerSaveResult
}
