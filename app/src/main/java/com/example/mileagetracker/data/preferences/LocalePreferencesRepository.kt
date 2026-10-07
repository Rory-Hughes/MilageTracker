package com.example.mileagetracker.data.preferences

import com.example.mileagetracker.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalePreferencesRepository @Inject constructor(
    private val dataSource: LocalePreferencesDataSource,
) {
    fun getLanguage(): Flow<AppLanguage> = dataSource.languageTag.map(AppLanguage::fromTag)

    suspend fun setLanguage(language: AppLanguage) {
        if (language == AppLanguage.SYSTEM_DEFAULT) {
            dataSource.clearLanguageTag()
        } else {
            dataSource.setLanguageTag(language.tag)
        }
    }

    suspend fun resetLanguage() {
        dataSource.clearLanguageTag()
    }
}
