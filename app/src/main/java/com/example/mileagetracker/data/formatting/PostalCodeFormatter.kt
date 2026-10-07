package com.example.mileagetracker.data.formatting

/**
 * Normalizes a raw string into Canada Post `A1A 1A1` postal code format.
 *
 * Steps:
 * 1. Strip all non-alphanumeric characters.
 * 2. Upper-case all letters.
 * 3. Verify exactly 6 characters remain.
 * 4. Insert a space after position 3.
 *
 * Returns `null` if the input cannot be normalized into a valid 6-character code.
 */
object PostalCodeFormatter {

    private val POSTAL_CODE_REGEX = Regex("^[A-Z][0-9][A-Z] [0-9][A-Z][0-9]$")

    /**
     * Normalizes [raw] to `A1A 1A1` format.
     *
     * @return formatted postal code string, or `null` if normalization is not possible.
     */
    fun format(raw: String): String? {
        val stripped = raw.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
        if (stripped.length != 6) return null
        val formatted = "${stripped.substring(0, 3)} ${stripped.substring(3)}"
        return if (POSTAL_CODE_REGEX.matches(formatted)) formatted else null
    }

    /**
     * Returns `true` if [postalCode] is already in valid `A1A 1A1` format.
     */
    fun isValid(postalCode: String): Boolean =
        POSTAL_CODE_REGEX.matches(postalCode)
}
