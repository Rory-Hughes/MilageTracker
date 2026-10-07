package com.example.mileagetracker.domain.validation

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import javax.inject.Inject

/**
 * Validates [VehicleEntity] records against four distinct rule sets.
 *
 * Reuses the shared [ValidationResult] / [ValidationError] / [ErrorCode]
 * types from [DriveValidator] so all validation feedback in the app is
 * uniform and the ViewModel never needs to try/catch for form validation.
 *
 * ## Rule sets
 *
 * | Method                       | Purpose |
 * |------------------------------|---------|
 * | [validateIdentity]           | Required name, plausible year, make/model |
 * | [validateOwnership]          | OwnerUid present, ownershipType in allowed set |
 * | [validateDefaultInvariant]   | At-most-one-default within a candidate list |
 * | [validateAll]                | Runs identity + ownership on a single entity |
 *
 * ## Default-vehicle invariant
 * [validateDefaultInvariant] operates on a **list** of vehicles for
 * a given (ownerUid, fiscalYear) rather than a single entity, because
 * the invariant is relational — it cannot be determined from one row
 * alone.  Call it before any write that touches `isDefault`.
 */
class VehicleValidator @Inject constructor() {

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 1 – Identity
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates the identity fields of a vehicle.
     *
     * Rules:
     * - [VehicleEntity.displayName] must not be blank.
     * - [VehicleEntity.displayName] must not exceed [MAX_DISPLAY_NAME_LENGTH]
     *   characters (UI rendering limit).
     * - [VehicleEntity.year], when provided, must be in
     *   `[MIN_VEHICLE_YEAR, MAX_VEHICLE_YEAR]`.
     * - [VehicleEntity.make], when provided, must not be blank (if the
     *   field is set it should contain meaningful content).
     * - [VehicleEntity.model], when provided, must not be blank.
     */
    fun validateIdentity(vehicle: VehicleEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (vehicle.displayName.isBlank()) {
            errors += ValidationError(
                field = "display_name",
                code = ErrorCode.BLANK_VEHICLE_NAME,
                message = "Vehicle name must not be blank.",
            )
        } else if (vehicle.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            errors += ValidationError(
                field = "display_name",
                code = ErrorCode.BLANK_VEHICLE_NAME,
                message = "Vehicle name must not exceed $MAX_DISPLAY_NAME_LENGTH " +
                    "characters (got ${vehicle.displayName.length}).",
            )
        }

        vehicle.year?.let { year ->
            if (year < MIN_VEHICLE_YEAR || year > MAX_VEHICLE_YEAR) {
                errors += ValidationError(
                    field = "year",
                    code = ErrorCode.INVALID_VEHICLE_YEAR,
                    message = "Vehicle year must be between $MIN_VEHICLE_YEAR " +
                        "and $MAX_VEHICLE_YEAR (got $year).",
                )
            }
        }

        vehicle.make?.let { make ->
            if (make.isBlank()) {
                errors += ValidationError(
                    field = "make",
                    code = ErrorCode.MISSING_REQUIRED_FIELD,
                    message = "Vehicle make must not be blank if provided.",
                )
            }
        }

        vehicle.model?.let { model ->
            if (model.isBlank()) {
                errors += ValidationError(
                    field = "model",
                    code = ErrorCode.MISSING_REQUIRED_FIELD,
                    message = "Vehicle model must not be blank if provided.",
                )
            }
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 2 – Ownership
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates ownership and assignment fields.
     *
     * Rules:
     * - [VehicleEntity.ownerUid] must not be blank.
     * - [VehicleEntity.ownershipType] must be one of the allowed values
     *   in [ALLOWED_OWNERSHIP_TYPES] (currently all three enum entries;
     *   exposed as a constant so future phases can restrict the set
     *   without changing the rule logic).
     * - [VehicleEntity.fiscalYear] must be in
     *   `[OdometerValidator.MIN_FISCAL_YEAR, OdometerValidator.MAX_FISCAL_YEAR]`.
     */
    fun validateOwnership(vehicle: VehicleEntity): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (vehicle.ownerUid.isBlank()) {
            errors += ValidationError(
                field = "owner_uid",
                code = ErrorCode.MISSING_OWNER_UID,
                message = "Vehicle must have a non-blank owner UID.",
            )
        }

        if (vehicle.ownershipType !in ALLOWED_OWNERSHIP_TYPES) {
            errors += ValidationError(
                field = "ownership_type",
                code = ErrorCode.INVALID_OWNERSHIP_TYPE,
                message = "Ownership type '${vehicle.ownershipType}' is not " +
                    "supported. Allowed values: " +
                    ALLOWED_OWNERSHIP_TYPES.joinToString { it.name } + ".",
            )
        }

        if (vehicle.fiscalYear !in OdometerValidator.MIN_FISCAL_YEAR..OdometerValidator.MAX_FISCAL_YEAR) {
            errors += ValidationError(
                field = "fiscal_year",
                code = ErrorCode.INVALID_FISCAL_YEAR,
                message = "Fiscal year must be between " +
                    "${OdometerValidator.MIN_FISCAL_YEAR} and " +
                    "${OdometerValidator.MAX_FISCAL_YEAR} " +
                    "(got ${vehicle.fiscalYear}).",
            )
        }

        return errors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Rule set 3 – Default-vehicle invariant
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Validates that at most one vehicle in [vehicles] has
     * [VehicleEntity.isDefault] == `true`.
     *
     * This is a **relational** rule: it requires the full list of vehicles
     * for a given (ownerUid, fiscalYear) pair, not a single entity.
     *
     * ## When to call
     * - Before any write that could change `isDefault` on multiple rows
     *   (e.g. a bulk import).
     * - In the vehicle-management ViewModel as a defensive check after
     *   loading the list from the database.
     * - In unit tests that verify the invariant after [VehicleRepository]
     *   mutations.
     *
     * Note: [VehicleRepositoryImpl.setDefaultVehicle] enforces this
     * invariant transactionally at the persistence layer.  This validator
     * provides an independent check at the domain/UI layer.
     *
     * @param vehicles the full active vehicle list for a single
     *                 (ownerUid, fiscalYear) pair.
     * @return [ValidationResult.Valid] if 0 or 1 vehicles are default;
     *         [ValidationResult.Invalid] listing every duplicate default
     *         vehicle's display name if > 1 are default.
     */
    fun validateDefaultInvariant(vehicles: List<VehicleEntity>): ValidationResult {
        val defaults = vehicles.filter { it.isDefault }

        if (defaults.size <= 1) return ValidationResult.Valid

        val names = defaults.joinToString { "'${it.displayName}'" }
        return ValidationResult.Invalid(
            listOf(
                ValidationError(
                    field = "is_default",
                    code = ErrorCode.MULTIPLE_DEFAULT_VEHICLES,
                    message = "More than one vehicle is marked as default: " +
                        "$names. Only one default vehicle is allowed per " +
                        "fiscal year.",
                ),
            ),
        )
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Aggregate
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Runs [validateIdentity] and [validateOwnership] on [vehicle] and
     * returns a merged [ValidationResult].
     *
     * Does **not** include [validateDefaultInvariant] because that rule
     * requires the full vehicle list, not a single entity.  Call it
     * separately when the list is available.
     */
    fun validateAll(vehicle: VehicleEntity): ValidationResult {
        val allErrors = listOf(
            validateIdentity(vehicle),
            validateOwnership(vehicle),
        ).filterIsInstance<ValidationResult.Invalid>()
            .flatMap { it.errors }
        return allErrors.toResult()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Constants
    // ───────────────────────────────────────────────────────────────────────────

    companion object {
        /** Maximum character length for [VehicleEntity.displayName]. */
        const val MAX_DISPLAY_NAME_LENGTH = 60

        /**
         * Earliest plausible vehicle model year.
         * The CRA has accepted mileage claims for vehicles manufactured
         * from at least 1950; 1900 gives comfortable headroom.
         */
        const val MIN_VEHICLE_YEAR = 1900

        /**
         * Latest plausible vehicle model year.
         * Allow two years ahead of the current year to cover newly-announced
         * models and pre-production vehicles.
         */
        val MAX_VEHICLE_YEAR: Int
            get() = java.util.Calendar.getInstance()
                .get(java.util.Calendar.YEAR) + 2

        /**
         * The set of [VehicleOwnershipType] values accepted by this validator.
         * Currently all three entries; exposed as a constant so future phases
         * can restrict the set (e.g. disallow COMPANY vehicles for solo-user
         * plan tiers) without changing rule logic.
         */
        val ALLOWED_OWNERSHIP_TYPES: Set<VehicleOwnershipType> =
            VehicleOwnershipType.entries.toSet()
    }
}
