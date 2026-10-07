package com.example.mileagetracker.data.local.entity

/**
 * Classifies the purpose of a recorded trip.
 *
 * Stored as its [name] string via a Room @TypeConverter so that reordering
 * entries never causes silent data corruption.
 *
 * Each entry exposes:
 * - [displayName]  — human-readable label shown in the UI.
 * - [icon]         — Material Icons name string (resolved at call-site via
 *                    reflection or a `when` mapper; kept as a String here to
 *                    avoid a Compose dependency in the data layer).
 * - [craHint]      — short CRA-deductibility hint shown under the chip.
 *
 * [UNCLASSIFIED] is intentionally excluded from category pickers via
 * [selectableEntries]; it is the default state only.
 */
enum class TripCategory(
    val displayName: String,
    val icon: String,
    val craHint: String,
) {
    UNCLASSIFIED(
        displayName = "Unclassified",
        icon = "HelpOutline",
        craHint = "Not yet reviewed",
    ),
    BUSINESS(
        displayName = "Business",
        icon = "BusinessCenter",
        craHint = "Deductible — CRA T2200 / T777",
    ),
    PERSONAL(
        displayName = "Personal",
        icon = "DirectionsCar",
        craHint = "Not deductible",
    ),
    COMMUTE(
        displayName = "Commute",
        icon = "Home",
        craHint = "Not deductible — regular home-to-office",
    ),
    MEDICAL(
        displayName = "Medical",
        icon = "LocalHospital",
        craHint = "Deductible — CRA line 33099 / 33199",
    ),
    CHARITY(
        displayName = "Charity",
        icon = "VolunteerActivism",
        craHint = "Deductible — CRA charitable travel",
    ),
    CUSTOM(
        displayName = "Custom",
        icon = "Edit",
        craHint = "Enter a purpose to determine deductibility",
    );

    companion object {
        /**
         * Categories shown in trip pickers and filter chips.
         * [UNCLASSIFIED] is excluded — it is a system default, not a user choice.
         */
        val selectableEntries: List<TripCategory> =
            entries.filter { it != UNCLASSIFIED }

        /**
         * Categories that contribute to CRA logbook totals.
         * Medical and Charity are both eligible alongside Business.
         */
        val craEligibleCategories: List<TripCategory> =
            listOf(BUSINESS, MEDICAL, CHARITY)
    }
}
