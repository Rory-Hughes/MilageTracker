package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.TripCategory

/**
 * Parameters required to generate a CRA logbook export.
 */
data class LogbookExportRequest(
    val vehicleId: Int?,
    val taxYear: Int,
    val format: ExportFormat,
    val categories: Set<TripCategory> = setOf(TripCategory.BUSINESS),
    val includeRouteDetails: Boolean = true,
)
