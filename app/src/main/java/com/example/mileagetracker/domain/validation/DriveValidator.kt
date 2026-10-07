package com.example.mileagetracker.domain.validation

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import javax.inject.Inject

/**
 * Validates a [DriveEntity] against four distinct rule sets.
 *
 * ## Design philosophy
 * Validation is separated from persistence (use cases) and from
 * presentation (ViewModels).  This class contains only pure business
 * rules; it has no knowledge of the database, coroutines, or Android
 * framework types.
 *
 * ## Rule sets
 *
 * | Method                    | Purpose |
 * |---------------------------|---------|
 * | [validateBasic]           | Minimum fields required for any save |
 * | [validateTiming]          | Time window sanity checks |
 * | [validateCraReady]        | All fields required for a CRA-compliant trip record |
 * | [validateCategoryPurpose] | Category ⇔ purpose-text coherence rules |
 * | [validateAll]             | Runs all four rule sets; returns merged error list |
 *
 * Each method returns a [ValidationResult] rather than throwing so that
 * the ViewModel can display inline field errors without try/catch.
 */
class DriveValidator @Inject constructor() {

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 1 – Basic
    // ───────────────────────────────────────────────────────────────────────────

    fun validateBasic(drive: DriveEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (drive.distanceKm < 0f) {
            errors += ValidationError(
                field = "distance_km",
                code = ErrorCode.NEGATIVE_DISTANCE,
                message = "Distance must be ≥ 0 km (got ${drive.distanceKm}).",
            )
        }
        if (drive.startedAt < MIN_VALID_EPOCH_MS) {
            errors += ValidationError(
                field = "started_at",
                code = ErrorCode.INVALID_TIMESTAMP,
                message = "Trip start time is not a valid date.",
            )
        }
        if (drive.driverUid.isNullOrBlank()) {
            errors += ValidationError(
                field = "driver_uid",
                code = ErrorCode.MISSING_REQUIRED_FIELD,
                message = "Driver UID is required.",
            )
        }
        if (drive.startLocation.isBlank()) {
            errors += ValidationError(
                field = "start_location",
                code = ErrorCode.MISSING_REQUIRED_FIELD,
                message = "Start location is required.",
            )
        }
        if (drive.endLocation.isBlank()) {
            errors += ValidationError(
                field = "end_location",
                code = ErrorCode.MISSING_REQUIRED_FIELD,
                message = "End location is required.",
            )
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 2 – Timing
    // ───────────────────────────────────────────────────────────────────────────

    fun validateTiming(drive: DriveEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val now = System.currentTimeMillis()

        if (drive.startedAt > now + CLOCK_SKEW_TOLERANCE_MS) {
            errors += ValidationError(
                field = "started_at",
                code = ErrorCode.FUTURE_TIMESTAMP,
                message = "Trip start time cannot be in the future.",
            )
        }
        val endedAt = drive.endedAt
        if (endedAt != null) {
            if (endedAt < drive.startedAt) {
                errors += ValidationError(
                    field = "ended_at",
                    code = ErrorCode.END_BEFORE_START,
                    message = "Trip end time must be after start time.",
                )
            } else {
                val durationMs = endedAt - drive.startedAt
                if (durationMs > MAX_TRIP_DURATION_MS) {
                    errors += ValidationError(
                        field = "ended_at",
                        code = ErrorCode.TRIP_TOO_LONG,
                        message = "Trip duration exceeds 24 hours; " +
                            "please verify the timestamps.",
                    )
                }
            }
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 3 – CRA-ready
    // ───────────────────────────────────────────────────────────────────────────

    fun validateCraReady(drive: DriveEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        validateCraTripState(drive, errors)
        validateCraTripFields(drive, errors)
        return errors.toResult()
    }

    private fun validateCraTripState(
        drive: DriveEntity,
        errors: MutableList<ValidationError>,
    ) {
        if (drive.tripCategory != TripCategory.BUSINESS) {
            errors += ValidationError(
                field = "trip_category",
                code = ErrorCode.NOT_BUSINESS_TRIP,
                message = "Only BUSINESS trips are CRA-reimbursable.",
            )
        }
        if (drive.classificationStatus != ClassificationStatus.USER_CONFIRMED) {
            errors += ValidationError(
                field = "classification_status",
                code = ErrorCode.NOT_USER_CONFIRMED,
                message = "Trip must be confirmed by the user before " +
                    "it can be included in a CRA report.",
            )
        }
        if (drive.vehicleId == null) {
            errors += ValidationError(
                field = "vehicle_id",
                code = ErrorCode.MISSING_VEHICLE,
                message = "A vehicle must be assigned for CRA reporting.",
            )
        }
    }

    private fun validateCraTripFields(
        drive: DriveEntity,
        errors: MutableList<ValidationError>,
    ) {
        if (drive.purposeText.isNullOrBlank()) {
            errors += ValidationError(
                field = "purpose_text",
                code = ErrorCode.MISSING_PURPOSE,
                message = "Trip purpose is required for CRA reporting.",
            )
        }
        if (drive.distanceKm <= 0f) {
            errors += ValidationError(
                field = "distance_km",
                code = ErrorCode.ZERO_DISTANCE,
                message = "Zero-distance trips cannot be included in a CRA report.",
            )
        }
        if (drive.appliedRateSnapshot <= 0f) {
            errors += ValidationError(
                field = "applied_rate_snapshot",
                code = ErrorCode.MISSING_RATE,
                message = "No reimbursement rate has been applied to this trip.",
            )
        }
        if (drive.startLocation.isBlank()) {
            errors += ValidationError(
                field = "start_location",
                code = ErrorCode.MISSING_REQUIRED_FIELD,
                message = "Start location is required for CRA reporting.",
            )
        }
        if (drive.endLocation.isBlank()) {
            errors += ValidationError(
                field = "end_location",
                code = ErrorCode.MISSING_REQUIRED_FIELD,
                message = "End location is required for CRA reporting.",
            )
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 4 – Category / purpose coherence
    // ───────────────────────────────────────────────────────────────────────────

    fun validateCategoryPurpose(drive: DriveEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        when (drive.tripCategory) {
            TripCategory.BUSINESS -> {
                if (drive.purposeText.isNullOrBlank()) {
                    errors += ValidationError(
                        field = "purpose_text",
                        code = ErrorCode.BUSINESS_REQUIRES_PURPOSE,
                        message = "Business trips require a purpose description.",
                    )
                }
            }
            TripCategory.UNCLASSIFIED -> {
                if (drive.classificationStatus == ClassificationStatus.USER_CONFIRMED) {
                    errors += ValidationError(
                        field = "classification_status",
                        code = ErrorCode.CONFIRMED_UNCLASSIFIED,
                        message = "A trip cannot be both UNCLASSIFIED and " +
                            "USER_CONFIRMED. Assign a category first.",
                    )
                }
            }
            TripCategory.PERSONAL,
            TripCategory.COMMUTE,
            TripCategory.MEDICAL,
            TripCategory.CHARITY,
            TripCategory.CUSTOM,
            -> { /* no hard errors */ }
        }

        drive.classificationConfidence?.let { confidence ->
            if (confidence !in 0f..1f) {
                errors += ValidationError(
                    field = "classification_confidence",
                    code = ErrorCode.INVALID_CONFIDENCE,
                    message = "Classification confidence must be between " +
                        "0.0 and 1.0 (got $confidence).",
                )
            }
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Aggregate
    // ───────────────────────────────────────────────────────────────────────────

    fun validateAll(drive: DriveEntity): ValidationResult {
        val allErrors = listOf(
            validateBasic(drive),
            validateTiming(drive),
            validateCategoryPurpose(drive),
            validateCraReady(drive),
        ).filterIsInstance<ValidationResult.Invalid>()
            .flatMap { it.errors }
        return allErrors.toResult()
    }

    companion object {
        private const val MIN_VALID_EPOCH_MS = 946_684_800_000L // Jan 1 2000
        const val MAX_TRIP_DURATION_MS = 24 * 60 * 60 * 1_000L
        const val CLOCK_SKEW_TOLERANCE_MS = 60_000L
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Result types  (shared by DriveValidator, OdometerValidator, VehicleValidator)
// ───────────────────────────────────────────────────────────────────────────

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(override val errors: List<ValidationError>) : ValidationResult()
    val isValid: Boolean get() = this is Valid
    open val errors: List<ValidationError>
        get() = if (this is Invalid) errors else emptyList()
}

data class ValidationError(
    val field: String,
    val code: ErrorCode,
    val message: String,
)

enum class ErrorCode {
    // Basic drive fields
    NEGATIVE_DISTANCE,
    INVALID_TIMESTAMP,
    MISSING_REQUIRED_FIELD,

    // Timing
    FUTURE_TIMESTAMP,
    END_BEFORE_START,
    TRIP_TOO_LONG,

    // CRA-ready
    NOT_BUSINESS_TRIP,
    NOT_USER_CONFIRMED,
    MISSING_VEHICLE,
    MISSING_PURPOSE,
    ZERO_DISTANCE,
    MISSING_RATE,

    // Category / purpose
    BUSINESS_REQUIRES_PURPOSE,
    CONFIRMED_UNCLASSIFIED,
    INVALID_CONFIDENCE,

    // Odometer (OdometerValidator)
    NEGATIVE_ODOMETER,
    ODOMETER_EXCEEDS_MAX,
    CLOSING_BEFORE_OPENING,
    ANNUAL_SPAN_TOO_LARGE,
    INVALID_FISCAL_YEAR,
    FUTURE_FISCAL_YEAR,

    // Vehicle (VehicleValidator)
    BLANK_VEHICLE_NAME,
    INVALID_VEHICLE_YEAR,
    MISSING_OWNER_UID,
    INVALID_OWNERSHIP_TYPE,
    MULTIPLE_DEFAULT_VEHICLES,
}

/**
 * Shared by [DriveValidator], [VehicleValidator], and [OdometerValidator].
 * Declared `internal` so all files in the validation package can resolve
 * it without needing to re-declare it.
 */
internal fun List<ValidationError>.toResult(): ValidationResult =
    if (isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(this)
