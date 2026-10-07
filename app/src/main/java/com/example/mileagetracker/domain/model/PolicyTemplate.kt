package com.example.mileagetracker.domain.model

/**
 * Domain model for a reimbursement policy template.
 *
 * A policy defines *what* trips qualify for reimbursement, *how* the rate
 * is determined, and *what* documentation is required.
 *
 * [RateMode] is defined in RateMode.kt in this same package.
 *
 * @property id                    Unique identifier (UUID string).
 * @property name                  Human-readable policy name shown in dropdowns.
 * @property rateMode              Whether to use the CRA standard rate or a custom rate.
 * @property customRateCentsPerKm  Required when [rateMode] is [RateMode.CUSTOM]; must be > 0.
 * @property eligibleCategories    Trip categories that qualify under this policy.
 * @property excludeCommute        When true, trips classified as COMMUTE are excluded.
 * @property requirePurpose        When true, drivers must supply a trip purpose/notes.
 * @property requireAttachment     When true, supporting documentation (e.g. receipt) is required.
 * @property maxDailyKm            Optional cap on claimable km per day; null = no cap.
 */
data class PolicyTemplate(
    val id: String,
    val name: String,
    val rateMode: RateMode = RateMode.CRA_STANDARD,
    val customRateCentsPerKm: Int? = null,
    val eligibleCategories: List<String> = emptyList(),
    val excludeCommute: Boolean = true,
    val requirePurpose: Boolean = false,
    val requireAttachment: Boolean = false,
    val maxDailyKm: Int? = null
)
