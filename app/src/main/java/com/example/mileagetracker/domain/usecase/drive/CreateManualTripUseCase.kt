package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.RateNotSeededException
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

/**
 * Creates a trip that was never auto-detected by the tracking service.
 *
 * ## Difference from [CreateDriveUseCase]
 * [CreateDriveUseCase] is the general-purpose creation path used by the
 * background tracking service. This use case is exclusively for the
 * form-based manual entry flow. It:
 *
 * 1. Forces [TrackingSource.MANUAL_ENTRY] regardless of what the caller passes,
 *    so the badge logic in the UI can always trust the source field.
 * 2. Sets [DriveEntity.classificationConfidence] to `1.0f` because user-confirmed
 *    data is treated as ground truth.
 * 3. Sets [DriveEntity.classificationStatus] to [ClassificationStatus.USER_CONFIRMED]
 *    when a [TripCategory] other than UNCLASSIFIED is supplied, or leaves it as
 *    [ClassificationStatus.PENDING] when the user skips classification.
 * 4. For CRA-eligible categories (BUSINESS / MEDICAL / CHARITY), computes and
 *    stores [DriveEntity.reimbursementAmountSnapshot] and
 *    [DriveEntity.appliedRateSnapshot] using the two-tier CRA formula so that
 *    the dashboard YTD deduction total is correct immediately after save.
 *    If no rate has been seeded yet the snapshot is left null and the save
 *    still succeeds.
 * 5. Writes a [AuditEventType.MANUAL_ENTRY] audit row (not DRIVE_CREATED) so
 *    that CRA audit trails clearly distinguish auto-tracked trips from
 *    manually entered ones.
 *
 * ## Validation
 * - [DriveEntity.startLocation] must be non-blank.
 * - [DriveEntity.endLocation] must be non-blank.
 * - [DriveEntity.distanceKm] must be > 0.
 * - [DriveEntity.startedAt] must be > 0.
 * - [DriveEntity.endedAt] must be > [DriveEntity.startedAt] when non-null.
 * - [DriveEntity.driverUid] must be non-blank.
 *
 * Throws [IllegalArgumentException] on validation failure so the ViewModel
 * can surface the message in the manual trip form.
 *
 * @see CreateDriveUseCase for auto-tracked trip creation.
 * @see UpdateDriveUseCase for the repair path.
 */
class CreateManualTripUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
    private val calculateReimbursementUseCase: CalculateReimbursementUseCase,
) {
    /**
     * @param draft partially-constructed entity from the form; `id` must be 0.
     * @param actorUid UID of the user submitting the form.
     * @return the generated database ID of the new drive.
     * @throws IllegalArgumentException if validation fails.
     */
    suspend operator fun invoke(draft: DriveEntity, actorUid: String): Int {
        validate(draft)

        val taxYear = taxYearOf(draft.startedAt)
        val reimbursement = computeReimbursement(
            category = draft.tripCategory,
            distanceKm = draft.distanceKm,
            driverUid = actorUid,
            taxYear = taxYear,
            tripStartMs = draft.startedAt,
        )

        val entity = draft.copy(
            trackingSource = TrackingSource.MANUAL_ENTRY,
            classificationConfidence = 1.0f,
            classificationStatus = if (draft.tripCategory.isClassified()) {
                ClassificationStatus.USER_CONFIRMED
            } else {
                ClassificationStatus.PENDING
            },
            driverUid = actorUid,
            appliedRateSnapshot = reimbursement?.appliedRateDollars ?: 0f,
            reimbursementAmountSnapshot = reimbursement?.amountDollars,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        val newId = driveRepository.saveDrive(entity)

        auditRepository.recordEvent(
            tripId = newId,
            actorUid = actorUid,
            eventType = AuditEventType.MANUAL_ENTRY,
            newValue = "trackingSource=MANUAL_ENTRY,confidence=1.0",
        )

        return newId
    }

    private suspend fun computeReimbursement(
        category: TripCategory,
        distanceKm: Float,
        driverUid: String,
        taxYear: Int,
        tripStartMs: Long,
    ): ReimbursementSnapshot? {
        if (category !in CRA_ELIGIBLE_CATEGORIES) return null

        return try {
            val yearStart = yearStartMillis(taxYear)
            val ytdBefore = driveRepository
                .getYearToDateBusinessDistance(
                    driverUid = driverUid,
                    fromMillis = yearStart,
                    toMillis = tripStartMs,
                )
                .first() ?: 0f

            val result = calculateReimbursementUseCase(
                taxYear = taxYear,
                newDistanceKm = distanceKm,
                ytdKmBefore = ytdBefore,
            )
            ReimbursementSnapshot(
                amountDollars = result.amountDollars.toFloat(),
                appliedRateDollars = result.firstTierRateCents / 100f,
            )
        } catch (error: RateNotSeededException) {
            missingReimbursementSnapshot(error)
        } catch (error: CraRateRegionRequiredException) {
            missingReimbursementSnapshot(error)
        }
    }

    private fun missingReimbursementSnapshot(error: Exception): ReimbursementSnapshot? =
        when (error) {
            is RateNotSeededException -> null
            is CraRateRegionRequiredException -> null
            else -> throw error
        }

    private fun validate(drive: DriveEntity) {
        require(drive.startLocation.isNotBlank()) {
            "Start location must not be blank."
        }
        require(drive.endLocation.isNotBlank()) {
            "End location must not be blank."
        }
        require(drive.distanceKm > 0f) {
            "Distance must be greater than 0 km, got ${drive.distanceKm}."
        }
        require(drive.startedAt > 0L) {
            "Start time must be a valid epoch millis timestamp."
        }
        if (drive.endedAt != null) {
            require(drive.endedAt > drive.startedAt) {
                "End time must be after start time."
            }
        }
        require(!drive.driverUid.isNullOrBlank()) {
            "Drive must have a non-blank driverUid."
        }
    }

    private fun taxYearOf(epochMs: Long): Int =
        Calendar.getInstance().also { it.timeInMillis = epochMs }.get(Calendar.YEAR)

    private fun yearStartMillis(year: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    companion object {
        private val CRA_ELIGIBLE_CATEGORIES = setOf(
            TripCategory.BUSINESS,
            TripCategory.MEDICAL,
            TripCategory.CHARITY,
        )
    }
}

private data class ReimbursementSnapshot(
    val amountDollars: Float,
    val appliedRateDollars: Float,
)

private fun TripCategory.isClassified(): Boolean =
    this != TripCategory.UNCLASSIFIED
