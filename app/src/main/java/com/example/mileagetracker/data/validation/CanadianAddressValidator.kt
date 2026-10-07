package com.example.mileagetracker.data.validation

import com.example.mileagetracker.data.formatting.PostalCodeFormatter
import com.example.mileagetracker.domain.model.CanadianAddress

/**
 * Validates individual fields of a [CanadianAddress].
 *
 * Each field returns a [ValidationResult] — either [ValidationResult.Valid] or
 * [ValidationResult.Invalid] carrying a human-readable reason.
 */
object CanadianAddressValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /** Validates that [provinceAbbreviation] is one of the 13 official Canadian codes. */
    fun validateProvince(provinceAbbreviation: String): ValidationResult {
        return if (provinceAbbreviation in CanadianAddress.PROVINCE_CODES) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("\"$provinceAbbreviation\" is not a valid Canadian province or territory code.")
        }
    }

    /**
     * Validates that [postalCode] matches the `[A-Z][0-9][A-Z] [0-9][A-Z][0-9]` pattern.
     * Accepts both already-formatted codes and raw unformatted input (will attempt normalization).
     */
    fun validatePostalCode(postalCode: String): ValidationResult {
        val normalized = PostalCodeFormatter.format(postalCode)
            ?: return ValidationResult.Invalid("Postal code must be in A1A 1A1 format.")
        return if (PostalCodeFormatter.isValid(normalized)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Postal code must be in A1A 1A1 format.")
        }
    }

    /** Validates all fields of the given [address] and returns a map of field name to result. */
    fun validate(address: CanadianAddress): Map<String, ValidationResult> = mapOf(
        "street" to if (address.street.isNotBlank()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Street address is required.")
        },
        "city" to if (address.city.isNotBlank()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("City is required.")
        },
        "province" to validateProvince(address.provinceAbbreviation),
        "postalCode" to validatePostalCode(address.postalCode)
    )
}
