package com.example.mileagetracker.domain.usecase.classification

import javax.inject.Inject

/**
 * Thin wrapper around [AutoClassifyTripUseCase] intended to be called
 * immediately after a new trip is persisted to the database.
 *
 * ## Caller
 * [DriveTrackingService] (or [CreateDriveUseCase]) should call this
 * use-case after the drive row is inserted and its ID is known.  The call
 * is fire-and-forget from the service perspective — a classification failure
 * must never prevent the trip from being saved.
 *
 * ## Behaviour
 * Delegates directly to [AutoClassifyTripUseCase] with `force = false`
 * (won't overwrite a human-set status) and the default rule set.
 * The result is returned so callers can log or surface it if desired,
 * but they are not required to act on it.
 *
 * ## Usage
 * ```kotlin
 * val result = autoClassifyNewTripUseCase(driveId = savedId)
 * Timber.d("Auto-classify result for trip $savedId: $result")
 * ```
 */
class AutoClassifyNewTripUseCase @Inject constructor(
    private val autoClassifyTripUseCase: AutoClassifyTripUseCase,
) {
    suspend operator fun invoke(
        driveId:  Int,
        actorUid: String = AutoClassifyTripUseCase.SYSTEM_ACTOR,
    ): AutoClassifyResult = autoClassifyTripUseCase(
        driveId  = driveId,
        actorUid = actorUid,
        force    = false,
    )
}
