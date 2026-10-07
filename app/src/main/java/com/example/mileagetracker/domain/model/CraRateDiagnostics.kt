package com.example.mileagetracker.domain.model

data class CraRateDiagnostics(
    val currentYear: Int,
    val catalogStartYear: Int,
    val catalogEndYear: Int,
    val seededYears: List<Int>,
    val unseededYears: List<Int>,
    val sourceUrl: String,
) {
    val currentYearSupported: Boolean
        get() = currentYear in catalogStartYear..catalogEndYear

    val currentYearSeeded: Boolean
        get() = currentYear in seededYears
}
