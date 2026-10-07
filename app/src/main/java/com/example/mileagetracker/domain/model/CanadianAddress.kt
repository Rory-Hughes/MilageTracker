package com.example.mileagetracker.domain.model

import java.util.Locale

/**
 * Domain model representing a Canadian mailing address.
 *
 * [postalCode] should be stored in normalized `A1A 1A1` format.
 */
data class CanadianAddress(
    val street: String,
    val city: String,
    val provinceAbbreviation: String,
    val postalCode: String
) {
    /**
     * Returns the full province/territory name in the given [locale].
     * Falls back to English for any locale other than French.
     */
    fun provinceName(locale: Locale = Locale.ENGLISH): String =
        if (locale.language == "fr") {
            PROVINCE_NAMES_FR[provinceAbbreviation] ?: provinceAbbreviation
        } else {
            PROVINCE_NAMES_EN[provinceAbbreviation] ?: provinceAbbreviation
        }

    companion object {
        /** All 10 provinces and 3 territories — the 13 official Canadian codes. */
        val PROVINCE_CODES: List<String> = listOf(
            "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT"
        )

        val PROVINCE_NAMES_EN: Map<String, String> = mapOf(
            "AB" to "Alberta",
            "BC" to "British Columbia",
            "MB" to "Manitoba",
            "NB" to "New Brunswick",
            "NL" to "Newfoundland and Labrador",
            "NS" to "Nova Scotia",
            "NT" to "Northwest Territories",
            "NU" to "Nunavut",
            "ON" to "Ontario",
            "PE" to "Prince Edward Island",
            "QC" to "Quebec",
            "SK" to "Saskatchewan",
            "YT" to "Yukon"
        )

        val PROVINCE_NAMES_FR: Map<String, String> = mapOf(
            "AB" to "Alberta",
            "BC" to "Colombie-Britannique",
            "MB" to "Manitoba",
            "NB" to "Nouveau-Brunswick",
            "NL" to "Terre-Neuve-et-Labrador",
            "NS" to "Nouvelle-Écosse",
            "NT" to "Territoires du Nord-Ouest",
            "NU" to "Nunavut",
            "ON" to "Ontario",
            "PE" to "Île-du-Prince-Édouard",
            "QC" to "Québec",
            "SK" to "Saskatchewan",
            "YT" to "Yukon"
        )
    }
}
