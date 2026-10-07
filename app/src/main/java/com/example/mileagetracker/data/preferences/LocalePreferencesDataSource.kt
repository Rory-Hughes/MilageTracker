package com.example.mileagetracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalePreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private companion object {
        val APP_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
    }

    val languageTag: Flow<String?> = dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE_TAG]
    }

    suspend fun setLanguageTag(tag: String) {
        dataStore.edit { prefs ->
            prefs[APP_LANGUAGE_TAG] = tag
        }
    }

    suspend fun clearLanguageTag() {
        dataStore.edit { prefs ->
            prefs.remove(APP_LANGUAGE_TAG)
        }
    }
}
