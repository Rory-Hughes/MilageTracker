package com.example.mileagetracker.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mileagetracker.domain.model.IntegrationConfig
import com.example.mileagetracker.domain.model.IntegrationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Persists [IntegrationConfig] objects in EncryptedSharedPreferences.
 * One entry per [IntegrationProvider], keyed by provider name.
 */
@Singleton
class IntegrationRepository @Inject constructor(
    @Named("integration") private val prefs: SharedPreferences
) {
    // ignoreUnknownKeys prevents crashes if config shape changes between app versions
    private val json = Json { ignoreUnknownKeys = true }

    fun getConfig(provider: IntegrationProvider): IntegrationConfig? {
        val raw = prefs.getString(provider.name, null) ?: return null
        return runCatching { json.decodeFromString<IntegrationConfig>(raw) }.getOrNull()
    }

    fun saveConfig(config: IntegrationConfig) {
        prefs.edit {
            putString(config.provider.name, json.encodeToString(config))
        }
    }

    fun revokeConfig(provider: IntegrationProvider) {
        prefs.edit {
            remove(provider.name)
        }
    }

    fun getAllConfigs(): List<IntegrationConfig> =
        IntegrationProvider.entries.mapNotNull { getConfig(it) }
}
