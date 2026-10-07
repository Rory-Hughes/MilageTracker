package com.example.mileagetracker.data.formatting

import com.example.mileagetracker.domain.model.CanadianAddress

/**
 * Builds a clean, single-line address from the **structured** `address` fields
 * returned by Nominatim / geocode.maps.co (requested via `addressdetails=1`),
 * rather than from the verbose `display_name` string.
 *
 * ## Why structured, not display_name
 * Nominatim's `display_name` is a full administrative breadcrumb, e.g.:
 *
 * ```
 * 2638, Westfield Road, Morna, Ketepec, City of Saint John,
 * Saint John County, New Brunswick / Nouveau-Brunswick, E2M 6L4, Canada
 * ```
 *
 * Trimming that by position is unreliable — you cannot tell "city" from
 * "neighbourhood" or "suburb" without the field names. The structured
 * `address` object names each component, so we can keep exactly:
 *
 * ```
 * 2638 Westfield Road, Saint John, NB, Canada
 * ```
 *
 * ## Output shape
 * `"<street>, <city>, <province>, <country>"` — any missing component is
 * dropped (with its separator). Postal code, county, neighbourhood, and
 * suburb are intentionally excluded to match the concise format shown in
 * trip lists and detail screens.
 */
object NominatimAddressFormatter {

    /**
     * @param address the Nominatim `address` object flattened to string values.
     * @return a clean single-line address, or `null` when nothing usable is
     *         present (the caller should then fall back to `display_name`).
     */
    fun format(address: Map<String, String>): String? {
        val parts = listOfNotNull(
            street(address),
            city(address),
            provinceAbbreviation(address),
            address["country"]?.trim()?.ifBlank { null },
        )
        return parts.joinToString(", ").ifBlank { null }
    }

    /**
     * House number + road joined with a space (no comma): "2638 Westfield Road".
     * Falls back through other named-way types, then neighbourhood/suburb, so a
     * point that isn't on a named road still gets a meaningful label.
     */
    private fun street(a: Map<String, String>): String? {
        val houseNumber = a["house_number"]?.trim().orEmpty()
        val road = firstNonBlank(
            a["road"], a["pedestrian"], a["footway"], a["cycleway"],
            a["path"], a["square"], a["neighbourhood"], a["suburb"],
        ).orEmpty()
        return listOf(houseNumber, road)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }
    }

    /** Most specific populated-place field, with the "City of " style prefix removed. */
    private fun city(a: Map<String, String>): String? =
        firstNonBlank(
            a["city"], a["town"], a["village"],
            a["municipality"], a["hamlet"], a["county"],
        )?.let(::stripAdministrativePrefix)

    /**
     * Two-letter province code. Prefers the unambiguous ISO subdivision code
     * (`CA-NB` → `NB`); falls back to mapping the (possibly bilingual) state
     * name through the canonical province tables.
     */
    private fun provinceAbbreviation(a: Map<String, String>): String? {
        val isoCode = a["ISO3166-2-lvl4"]?.trim()
            ?.takeIf { it.startsWith(CANADA_ISO_PREFIX) }
            ?.removePrefix(CANADA_ISO_PREFIX)
        // Nominatim sometimes renders "New Brunswick / Nouveau-Brunswick";
        // try each half before falling back to the whole string.
        return isoCode ?: a["state"]?.trim()?.ifBlank { null }?.let { state ->
            state.split("/").firstNotNullOfOrNull { part -> STATE_NAME_TO_ABBREV[part.trim()] }
                ?: STATE_NAME_TO_ABBREV[state]
        }
    }

    private fun stripAdministrativePrefix(city: String): String {
        val trimmed = city.trim()
        return ADMIN_PREFIXES
            .firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
            ?.let { trimmed.substring(it.length).trim() }
            ?: trimmed
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private const val CANADA_ISO_PREFIX = "CA-"

    private val ADMIN_PREFIXES = listOf(
        "City of ", "Town of ", "Municipality of ",
        "Village of ", "District of ", "Township of ",
    )

    /** Reverse of the canonical province tables: full name (EN or FR) → code. */
    private val STATE_NAME_TO_ABBREV: Map<String, String> = buildMap {
        CanadianAddress.PROVINCE_NAMES_EN.forEach { (code, name) -> put(name, code) }
        CanadianAddress.PROVINCE_NAMES_FR.forEach { (code, name) -> put(name, code) }
    }
}
