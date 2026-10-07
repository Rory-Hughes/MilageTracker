package com.example.mileagetracker.domain.model

/**
 * Controls which reimbursement rate is used in [GetTripSummaryUseCase].
 *
 * - [CRA_STANDARD] — use the seeded [CraRateEntity] rows from [RateRepository];
 *   the two-tier (first-tier / additional) rates apply.
 * - [CUSTOM] — use the flat rate (in $/km) stored in [SettingsRepository];
 *   bypasses the CRA two-tier logic entirely and applies the single rate to
 *   all business km.
 */
enum class RateMode {
    CRA_STANDARD,
    CUSTOM,
}
