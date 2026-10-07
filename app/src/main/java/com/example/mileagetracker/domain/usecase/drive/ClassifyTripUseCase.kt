package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import java.util.Calendar
import javax.inject.Inject

/**
 * Classifies a trip with a given [TripCategory] and optional purpose text.
 *
 * ## What it does
 * 1. Loads the current drive from [DriveRepository].
 * 2. Looks up the active CRA rate for the trip’s fiscal year via
 *    [RateRepository.getRateForYearOnce] (suspend, one-shot).
 * 3. Converts the rate from **cents per km** to **dollars per km**
 *    (`firstTierRateCents / 100f`) and stores it as [DriveEntity.appliedRateSnapshot].
 * 4. Computes a reimbursement snapshot for BUSINESS trips
 *    (`distanceKm × ratePerKm`).
 * 5. Delegates to [UpdateDriveUseCase], which persists the entity and writes
 *    per-field audit rows to `trip_audit`.
 *
 * ## CUSTOM category rule
 * [purposeText] must be non-blank when [category] is [TripCategory.CUSTOM].
 * Throws [IllegalArgumentException] otherwise.
 *
 * @return `true` if the trip was found and saved; `false` if not found / deleted.
 */
class ClassifyTripUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val updateDriveUseCase: UpdateDriveUseCase,
    private val calculateReimbursementUseCase: CalculateReimbursementUseCase,
    private val currentUserProvider: CurrentUserProvider,
) {
    suspend operator fun invoke(
        driveId: Int,
        category: TripCategory,
        purposeText: String? = null,
        actorUid: String? = null,
    ): Boolean {
        require(category != TripCategory.CUSTOM || !purposeText.isNullOrBlank()) {
            "A purpose description is required for CUSTOM trips."
        }

        val drive = driveRepository.getDriveById(driveId) ?: return false

        val fiscalYear = Calendar.getInstance().apply {
            timeInMillis = drive.startedAt
        }.get(Calendar.YEAR)

        val reimbursementResult = if (category == TripCategory.BUSINESS) {
            calculateReimbursementUseCase(
                taxYear = fiscalYear,
                newDistanceKm = drive.distanceKm,
            )
        } else {
            null
        }
        val ratePerKm = reimbursementResult?.firstTierRateCents?.div(100f) ?: 0f
        val reimbursement = reimbursementResult?.amountDollars?.toFloat()

        val updated = drive.copy(
            tripCategory = category,
            classificationStatus = ClassificationStatus.USER_CONFIRMED,
            purposeText = purposeText?.takeIf { it.isNotBlank() },
            appliedRateSnapshot = ratePerKm,
            reimbursementAmountSnapshot = reimbursement,
            updatedAt = System.currentTimeMillis(),
        )

        return updateDriveUseCase(updated, actorUid ?: currentUserProvider.requireUid())
    }
}
