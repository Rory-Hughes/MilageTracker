package com.example.mileagetracker.domain.usecase.suggestion

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import javax.inject.Inject

/**
 * Applies a [TripSuggestion] to an existing trip and records an audit row
 * capturing that the classification originated from a suggestion.
 *
 * ## Steps
 * 1. Delegates to [ClassifyTripUseCase] with the suggestion's [category] and
 *    [purposeText], which handles the rate snapshot, reimbursement calc, and
 *    per-field audit diff via [UpdateDriveUseCase].
 * 2. After a successful save, writes one additional [AuditEventType.SUGGESTION_ACCEPTED]
 *    row recording the suggestion's [matchConfidence] and the IDs of the
 *    historical trips it was based on.
 *
 * @return `true` if the trip was found and saved; `false` if not found.
 */
class ApplySuggestionUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
    private val classifyTripUseCase: ClassifyTripUseCase,
    private val currentUserProvider: CurrentUserProvider,
) {
    suspend operator fun invoke(
        tripId: Int,
        suggestion: TripSuggestion,
        actorUid: String = currentUserProvider.requireUid(),
    ): Boolean {
        val saved = classifyTripUseCase(
            driveId = tripId,
            category = suggestion.suggestedCategory,
            purposeText = suggestion.suggestedPurpose,
            actorUid = actorUid,
        )

        if (!saved) return false

        // Optionally apply the suggested vehicle if one was recorded.
        suggestion.suggestedVehicleId?.let { vehicleId ->
            driveRepository.getDriveById(tripId)?.let { refreshed ->
                if (refreshed.vehicleId == null) {
                    driveRepository.updateDrive(
                        refreshed.copy(
                            vehicleId = vehicleId,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }

        auditRepository.recordEvent(
            tripId = tripId,
            actorUid = actorUid,
            eventType = AuditEventType.SUGGESTION_ACCEPTED,
            fieldName = null,
            oldValue = null,
            newValue = buildString {
                append("confidence=")
                append("%.2f".format(suggestion.matchConfidence))
                append(",basedOn=")
                append(suggestion.basedOnTripIds.joinToString("|"))
            },
        )

        return true
    }
}
