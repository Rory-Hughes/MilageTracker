package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.preferences.UserPreferencesRepository
import javax.inject.Inject

/**
 * Invoked at first-run or during onboarding to seed DataStore with Canadian
 * unit and currency preferences if they have not already been set.
 *
 * The idempotency guard lives in [UserPreferencesRepository.applyCanadianDefaultsIfNotSet],
 * so this use case is safe to call on every app launch.
 */
class ApplyCanadianDefaultsUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke() {
        userPreferencesRepository.applyCanadianDefaultsIfNotSet()
    }
}
