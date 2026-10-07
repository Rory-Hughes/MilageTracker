package com.example.mileagetracker.domain.usecase.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import javax.inject.Inject

class ResetLanguagePreferenceUseCase @Inject constructor(
    private val repository: LocalePreferencesRepository,
) {
    suspend operator fun invoke() {
        repository.resetLanguage()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
