package com.example.mileagetracker.domain.usecase.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import javax.inject.Inject

class SetLanguagePreferenceUseCase @Inject constructor(
    private val repository: LocalePreferencesRepository,
) {
    suspend operator fun invoke(language: AppLanguage) {
        repository.setLanguage(language)

        val locales = if (language == AppLanguage.SYSTEM_DEFAULT) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }

        AppCompatDelegate.setApplicationLocales(locales)
    }
}
