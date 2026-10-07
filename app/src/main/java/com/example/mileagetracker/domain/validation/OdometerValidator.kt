package com.example.mileagetracker.domain.validation

import com.example.mileagetracker.data.local.entity.VehicleEntity
import javax.inject.Inject

/**
 * Validates odometer readings for [VehicleEntity] records.
 *
 * ## Design
 * Reuses the [ValidationResult] / [ValidationError] / [ErrorCode] types
 * from [DriveValidator] so all validation feedback in the app is uniform.
 * This class is pure (no I/O, no coroutines) and can be used in both
 * ViewModel form-validation and use-case pre-condition checks.
 *
 * ## Rule sets
 *
 * | Method                        | Purpose |
 * |-------------------------------|---------|
 * | [validateReading]             | Single value: non-negative, plausible upper bound |
 * | [validatePair]                | Opening vs closing coherence |
 * | [validateFiscalYearCombination] | Cross-check year, odometer pair, and annual span |
 * | [validateVehicle]             | Convenience: runs all applicable sets on a [VehicleEntity] |
 */
class OdometerValidator @Inject constructor() {

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 1 – Single reading
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates a single odometer reading in km.
     *
     * Rules:
     * - [readingKm] must be ≥ 0 (no negative odometers).
     * - [readingKm] must be ≤ [MAX_PLAUSIBLE_ODOMETER_KM] (2,000,000 km).
     *   Values above this are almost certainly data-entry errors.
     *
     * @param readingKm  the odometer value to validate.
     * @param fieldName  snake_case field name for the error record
     *                   (e.g. `"opening_odometer_km"`).
     */
    fun validateReading(
        readingKm: Float,
        fieldName: String = "odometer_km",
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (readingKm < 0f) {
            errors += ValidationError(
                field = fieldName,
                code = ErrorCode.NEGATIVE_ODOMETER,
                message = "Odometer reading must be ≥ 0 km (got $readingKm).",
            )
        }

        if (readingKm > MAX_PLAUSIBLE_ODOMETER_KM) {
            errors += ValidationError(
                field = fieldName,
                code = ErrorCode.ODOMETER_EXCEEDS_MAX,
                message = "Odometer reading of $readingKm km exceeds the " +
                    "maximum plausible value of $MAX_PLAUSIBLE_ODOMETER_KM km. " +
                    "Please check the value.",
            )
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 2 – Opening / closing pair
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates an opening/closing odometer pair.
     *
     * Rules:
     * - Each individual reading is validated via [validateReading].
     * - [closingKm] must be ≥ [openingKm].
     * - The annual span ([closingKm] - [openingKm]) must be ≤
     *   [MAX_ANNUAL_SPAN_KM] (200,000 km).
     *
     * @param openingKm  the odometer at the start of the fiscal year.
     * @param closingKm  the odometer at the end of the fiscal year.
     */
    fun validatePair(
        openingKm: Float,
        closingKm: Float,
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        errors += validateReading(openingKm, "opening_odometer_km").errors
        errors += validateReading(closingKm, "closing_odometer_km").errors

        if (closingKm < openingKm) {
            errors += ValidationError(
                field = "closing_odometer_km",
                code = ErrorCode.CLOSING_BEFORE_OPENING,
                message = "Closing odometer ($closingKm km) must be ≥ " +
                    "opening odometer ($openingKm km).",
            )
        } else {
            val span = closingKm - openingKm
            if (span > MAX_ANNUAL_SPAN_KM) {
                errors += ValidationError(
                    field = "closing_odometer_km",
                    code = ErrorCode.ANNUAL_SPAN_TOO_LARGE,
                    message = "Annual odometer span of ${span.toInt()} km exceeds " +
                        "the maximum expected value of " +
                        "${MAX_ANNUAL_SPAN_KM.toInt()} km. " +
                        "Please verify the readings.",
                )
            }
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 3 – Fiscal year combination
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates the combination of fiscal year and odometer readings.
     *
     * @param fiscalYear  the tax year to validate.
     * @param openingKm   opening odometer for the year, or `null` if not set.
     * @param closingKm   closing odometer for the year, or `null` if not set.
     */
    fun validateFiscalYearCombination(
        fiscalYear: Int,
        openingKm: Float? = null,
        closingKm: Float? = null,
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (fiscalYear < MIN_FISCAL_YEAR || fiscalYear > MAX_FISCAL_YEAR) {
            errors += ValidationError(
                field = "fiscal_year",
                code = ErrorCode.INVALID_FISCAL_YEAR,
                message = "Fiscal year must be between $MIN_FISCAL_YEAR " +
                    "and $MAX_FISCAL_YEAR (got $fiscalYear).",
            )
        }

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (fiscalYear > currentYear + 1) {
            errors += ValidationError(
                field = "fiscal_year",
                code = ErrorCode.FUTURE_FISCAL_YEAR,
                message = "Fiscal year $fiscalYear is more than one year " +
                    "in the future.",
            )
        }

        when {
            openingKm != null && closingKm != null ->
                errors += validatePair(openingKm, closingKm).errors
            openingKm != null ->
                errors += validateReading(openingKm, "opening_odometer_km").errors
            closingKm != null ->
                errors += validateReading(closingKm, "closing_odometer_km").errors
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Convenience: full vehicle validation
    // ───────────────────────────────────────────────────────────────────────────

    fun validateVehicle(vehicle: VehicleEntity): ValidationResult =
        validateFiscalYearCombination(
            fiscalYear = vehicle.fiscalYear,
            openingKm = vehicle.openingOdometerKm,
            closingKm = vehicle.closingOdometerKm,
        )

    // ───────────────────────────────────────────────────────────────────────────
    // Constants
    // ───────────────────────────────────────────────────────────────────────────

    companion object {
        const val MIN_ODOMETER_KM = 0f
        const val MAX_PLAUSIBLE_ODOMETER_KM = 2_000_000f
        const val MAX_ANNUAL_SPAN_KM = 200_000f
        const val MIN_FISCAL_YEAR = 2000
        const val MAX_FISCAL_YEAR = 2100
    }
}
