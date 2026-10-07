package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Produces a live list of [TripException] objects for all of a user's trips.
 */
class GetTripExceptionsUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
) {

    operator fun invoke(driverUid: String): Flow<List<TripException>> =
        driveRepository.getAllDrives(driverUid).combine(flowOf(Unit)) { drives, _ ->
            buildExceptions(drives)
        }

    private suspend fun buildExceptions(drives: List<DriveEntity>): List<TripException> {
        val exceptions = mutableListOf<TripException>()

        for (drive in drives) {
            val latestAudit = auditRepository
                .getAuditTrailForTripOnce(drive.id)
                .lastOrNull()

            val exceptionType = detectExceptionType(
                drive = drive,
                latestAuditType = latestAudit?.eventType,
            ) ?: continue

            exceptions += TripException(
                tripId = drive.id,
                exceptionType = exceptionType,
                detectedAt = latestAudit?.changedAt ?: drive.startedAt,
                resolvedAt = null,
            )
        }

        return exceptions.sortedByDescending { it.detectedAt }
    }

    private fun detectExceptionType(
        drive: DriveEntity,
        latestAuditType: AuditEventType?,
    ): ExceptionType? = when {
        drive.classificationStatus == ClassificationStatus.NEEDS_REVIEW &&
            latestAuditType == AuditEventType.DUPLICATE_FLAGGED ->
            ExceptionType.DUPLICATE_FLAGGED

        drive.classificationStatus == ClassificationStatus.NEEDS_REVIEW ->
            ExceptionType.NEEDS_REVIEW

        TripReadinessRules.isTrackedForReportReadiness(drive) &&
            (
                drive.classificationStatus == ClassificationStatus.INCOMPLETE ||
                    TripReadinessRules.missingReportReadinessFields(drive).isNotEmpty()
                ) ->
            ExceptionType.INCOMPLETE_DATA

        isImpossibleRoute(drive) ->
            ExceptionType.IMPOSSIBLE_ROUTE

        drive.classificationStatus == ClassificationStatus.USER_CORRECTED &&
            latestAuditType != AuditEventType.TRIP_REPAIRED ->
            ExceptionType.MANUAL_OVERRIDE

        else -> null
    }

    private fun isImpossibleRoute(drive: DriveEntity): Boolean {
        val endedAt = drive.endedAt ?: return false
        return drive.distanceKm < 0f || endedAt < drive.startedAt
    }
}
