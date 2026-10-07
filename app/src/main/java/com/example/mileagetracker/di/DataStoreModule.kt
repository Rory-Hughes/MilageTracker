package com.example.mileagetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the app-wide [DataStore<Preferences>] singleton.
 *
 * The DataStore is created lazily via the [preferencesDataStore] property
 * delegate, which guarantees a single instance per [Context] even if
 * [provideDataStore] is called from multiple injection sites.
 *
 * ## File name
 * The DataStore file is named `"user_settings"` — it lives in the app's
 * private data directory and is not backed up by default (add a
 * `backup_rules.xml` exclusion if cloud-backup is enabled later).
 */
private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "user_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
