package com.example.mileagetracker.domain.usecase.localization

import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLanguagePreferenceUseCase @Inject constructor(
    private val repository: LocalePreferencesRepository,
) {
    operator fun invoke(): Flow<AppLanguage> = repository.getLanguage()
}
