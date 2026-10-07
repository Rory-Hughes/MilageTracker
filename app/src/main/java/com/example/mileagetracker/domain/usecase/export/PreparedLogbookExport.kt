package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity

data class PreparedLogbookExport(
    val vehicleId: Int?,
    val taxYear: Int,
    val categories: List<TripCategory>,
    val includeRouteDetails: Boolean,
    val scopeLabel: String,
    val scopeToken: String,
    val sections: List<PreparedLogbookSection>,
    val totals: PreparedLogbookTotals,
) {
    val isBusinessOnly: Boolean
        get() = categories == listOf(TripCategory.BUSINESS)

    val categorySummary: String
        get() = categories.joinToString(", ") { it.displayName }

    val distanceLabel: String
        get() = if (isBusinessOnly) "Business km" else "Selected-category km"

    val percentLabel: String
        get() = if (isBusinessOnly) "Business use %" else "Selected-category use %"
}

data class PreparedLogbookSection(
    val vehicleId: Int?,
    val vehicle: VehicleEntity?,
    val label: String,
    val trips: List<PreparedLogbookTrip>,
    val totals: PreparedLogbookTotals,
)

data class PreparedLogbookTrip(
    val tripId: Int,
    val startedAt: Long,
    val startLocation: String,
    val endLocation: String,
    val distanceKm: Double,
    val tripCategory: TripCategory,
    val notes: String,
    val vehicleLabel: String,
)

data class PreparedLogbookTotals(
    val totalTripCount: Int,
    val exportedTripCount: Int,
    val totalKm: Double,
    val selectedKm: Double,
    val selectedUsePercent: Double,
)
