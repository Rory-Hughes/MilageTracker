package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.ReportReadiness
import javax.inject.Inject

/**
 * Scores a collection of trips for CRA report readiness.
 *
 * Pure, stateless use-case that accepts a snapshot list of [DriveEntity] rows
 * and returns a [ReportReadiness] per tracked trip sorted by score ascending
 * so the worst offenders appear first in the exceptions flow.
 */
class GetReportReadinessUseCase @Inject constructor() {

    operator fun invoke(drives: List<DriveEntity>): List<ReportReadiness> =
        drives
            .filter(TripReadinessRules::isTrackedForReportReadiness)
            .map(::score)
            .sortedBy { it.readinessScore }

    private fun score(drive: DriveEntity): ReportReadiness {
        val missing = TripReadinessRules.missingReportReadinessFields(drive)
        val readinessScore = (4 - missing.size) * 0.25f
        return ReportReadiness(
            tripId = drive.id,
            missingFields = missing,
            readinessScore = readinessScore,
        )
    }
}
