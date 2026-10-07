package com.example.mileagetracker.data.formatting

import com.example.mileagetracker.domain.model.CanadianAddress
import java.util.Locale

/**
 * Formats a [CanadianAddress] into the canonical Canadian mailing address
 * string used in PDF and CSV export output.
 *
 * Canada Post standard multi-line format:
 * ```
 * Street
 * City  Province  PostalCode
 * ```
 *
 * Single-line export format (used in table cells and CSV columns):
 * ```
 * Street, City, Province  PostalCode
 * ```
 *
 * Note the double space between Province and PostalCode — this matches
 * Canada Post addressing guidelines.
 */
object CanadianAddressFormatter {

    /**
     * Returns the address as a single-line string for use in CSV cells or
     * narrow PDF table columns.
     *
     * Example: `"123 Main St, Saint John, NB  E2L 4Z6"`
     *
     * @param address   the [CanadianAddress] to format.
     * @return single-line address string; empty string if all fields are blank.
     */
    fun formatForExport(address: CanadianAddress): String {
        val parts = buildList {
            if (address.street.isNotBlank()) add(address.street.trim())
            val cityProvince = buildString {
                if (address.city.isNotBlank()) append(address.city.trim())
                if (address.provinceAbbreviation.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(address.provinceAbbreviation.trim().uppercase())
                }
                if (address.postalCode.isNotBlank()) {
                    val normalized = PostalCodeFormatter.format(address.postalCode)
                        ?: address.postalCode.trim().uppercase()
                    // Double space before postal code per Canada Post convention
                    append("  $normalized")
                }
            }
            if (cityProvince.isNotBlank()) add(cityProvince)
        }
        return parts.joinToString(", ")
    }

    /**
     * Returns the address formatted as a two-line string suitable for PDF
     * cover pages and letter-style headers.
     *
     * ```
     * 123 Main St
     * Saint John, NB  E2L 4Z6
     * Canada
     * ```
     *
     * @param address   the [CanadianAddress] to format.
     * @param includeCountry whether to append "Canada" as the final line.
     * @return multi-line address string.
     */
    fun formatMultiLine(
        address: CanadianAddress,
        includeCountry: Boolean = true
    ): String {
        val lines = buildList {
            if (address.street.isNotBlank()) add(address.street.trim())
            val line2 = buildString {
                if (address.city.isNotBlank()) append(address.city.trim())
                if (address.provinceAbbreviation.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(address.provinceAbbreviation.trim().uppercase())
                }
                if (address.postalCode.isNotBlank()) {
                    val normalized = PostalCodeFormatter.format(address.postalCode)
                        ?: address.postalCode.trim().uppercase()
                    append("  $normalized")
                }
            }
            if (line2.isNotBlank()) add(line2)
            if (includeCountry) add("Canada")
        }
        return lines.joinToString("\n")
    }

    /**
     * Attempts to parse a raw free-text address string into the canonical
     * single-line export format.
     *
     * If the [raw] string already matches the `A1A 1A1` postal-code pattern,
     * it is normalised in-place. Otherwise the string is returned trimmed.
     * This is used to normalise location strings coming from the GPS/geocoder
     * before writing them to PDF/CSV output.
     *
     * @param raw raw address string from GPS geocoder or user input.
     * @return normalised string ready for export.
     */
    fun normalizeRawLocationForExport(raw: String): String {
        if (raw.isBlank()) return ""
        // Attempt to find and normalize a Canadian postal code anywhere in the string
        val postalPattern = Regex("[A-Za-z][0-9][A-Za-z]\\s?[0-9][A-Za-z][0-9]")
        return postalPattern.replace(raw.trim()) { match ->
            PostalCodeFormatter.format(match.value) ?: match.value.uppercase()
        }
    }
}
