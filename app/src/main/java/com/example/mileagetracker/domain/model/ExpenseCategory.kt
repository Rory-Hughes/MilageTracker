package com.example.mileagetracker.domain.model

/**
 * Sealed hierarchy of vehicle expense categories recognised by the CRA for
 * motor vehicle expense claims (T2125 / T776 / T2200).
 *
 * Each subclass carries a human-readable [displayName] and a short
 * [craNote] surfaced as a tooltip / info icon in [ExpenseSummaryScreen].
 *
 * ## Room storage
 * [VehicleExpenseEntity] stores the category as a discriminator string
 * obtained via [key].  The sealed class is reconstituted in the repository
 * mapping layer via [fromKey].
 *
 * ## Usage
 * ```kotlin
 * val category: ExpenseCategory = ExpenseCategory.Fuel
 * println(category.displayName)  // "Fuel"
 * println(category.key)          // "Fuel"
 * ```
 */

private const val TOLLS_CRA_NOTE =
    "Bridge and highway tolls incurred on business trips are fully deductible."

private const val PARKING_CRA_NOTE =
    "Business parking fees are 100 % deductible; commuting parking is not."

private const val INSURANCE_CRA_NOTE =
    "Annual vehicle insurance premiums. Deductible at the business-use percentage."

private const val MAINTENANCE_CRA_NOTE =
    "Servicing, tires, brakes, and other upkeep costs. Deductible at the business-use percentage."

private const val FEUL_CRA_NOTE =
    "Gasoline, diesel, and oil used for the vehicle. Deductible at the business-use percentage."

private const val LEASE_CRA_NOTE =
    "Monthly lease payments are deductible at the business-use percentage, " +
        "subject to the CRA prescribed lease limit."

private const val LOAN_INTEREST_CRA_NOTE =
    "Interest on money borrowed to purchase a vehicle, " +
        "deductible at the business-use percentage up to the CRA annual limit."

private const val REGISTRATION_CRA_NOTE =
    "Annual licence and registration fees, deductible at the business-use percentage."

sealed class ExpenseCategory(
    val displayName: String,
    val craNote: String,
) {
    /** Returns the stable storage key used in [VehicleExpenseEntity.categoryKey]. */
    open val key: String get() = this::class.simpleName!!

    // ── Fixed categories ─────────────────────────────────────────────────────

    object Fuel : ExpenseCategory(
        displayName = "Fuel & Oil",
        craNote = FEUL_CRA_NOTE,
    )

    object Maintenance : ExpenseCategory(
        displayName = "Maintenance & Repairs",
        craNote = MAINTENANCE_CRA_NOTE,
    )

    object Insurance : ExpenseCategory(
        displayName = "Insurance",
        craNote = INSURANCE_CRA_NOTE,
    )

    object Parking : ExpenseCategory(
        displayName = "Parking",
        craNote = PARKING_CRA_NOTE,
    )

    object Tolls : ExpenseCategory(
        displayName = "Tolls & Highway Fees",
        craNote = TOLLS_CRA_NOTE,
    )

    object Lease : ExpenseCategory(
        displayName = "Lease Payments",
        craNote = LEASE_CRA_NOTE,
    )

    object LoanInterest : ExpenseCategory(
        displayName = "Loan Interest",
        craNote = LOAN_INTEREST_CRA_NOTE,
    )

    object Registration : ExpenseCategory(
        displayName = "Licence & Registration",
        craNote = REGISTRATION_CRA_NOTE,
    )

    // ── Open-ended category ──────────────────────────────────────────────────

    /**
     * Catch-all category with a user-supplied [label] (e.g. "Car wash").
     *
     * [key] is prefixed with `"Other:"` so that it round-trips correctly
     * through [fromKey] without colliding with fixed-category names.
     */
    data class Other(val label: String) : ExpenseCategory(
        displayName = label,
        craNote = "Other vehicle expense. Consult a tax professional to confirm deductibility.",
    ) {
        override val key: String get() = "Other:$label"
    }

    companion object {
        /** All fixed (non-[Other]) categories in display order. */
        val fixed: List<ExpenseCategory> = listOf(
            Fuel,
            Maintenance,
            Insurance,
            Parking,
            Tolls,
            Lease,
            LoanInterest,
            Registration,
        )

        /**
         * Reconstitutes an [ExpenseCategory] from its [key] string.
         *
         * - Fixed categories are matched by class simple name.
         * - [Other] categories are matched by the `"Other:"` prefix.
         * - Unrecognised keys fall back to `Other(key)` rather than throwing,
         *   so old data survives schema changes gracefully.
         */
        fun fromKey(key: String): ExpenseCategory = when (key) {
            "Fuel" -> Fuel
            "Maintenance" -> Maintenance
            "Insurance" -> Insurance
            "Parking" -> Parking
            "Tolls" -> Tolls
            "Lease" -> Lease
            "LoanInterest" -> LoanInterest
            "Registration" -> Registration
            else -> Other(key.removePrefix("Other:"))
        }
    }
}
