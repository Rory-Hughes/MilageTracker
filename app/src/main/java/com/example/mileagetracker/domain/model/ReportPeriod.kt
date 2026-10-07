package com.example.mileagetracker.domain.model

/**
 * Granularity of a trip summary report.
 *
 * Used by [GetTripSummaryUseCase] to determine how to bucket drives into
 * [TripSummary] rows, and by [ReportsViewModel] to drive the tab-row UI.
 *
 * - [MONTHLY]   — one [TripSummary] per calendar month.
 * - [QUARTERLY] — one [TripSummary] per calendar quarter (Q1–Q4).
 * - [ANNUAL]    — one [TripSummary] per calendar year.
 */
enum class ReportPeriod {
    MONTHLY,
    QUARTERLY,
    ANNUAL,
}
