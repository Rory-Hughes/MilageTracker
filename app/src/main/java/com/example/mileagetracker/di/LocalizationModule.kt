package com.example.mileagetracker.di

import com.example.mileagetracker.data.formatting.CanadianCurrencyFormatter
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.data.preferences.UserPreferencesRepository
import com.example.mileagetracker.domain.usecase.ApplyCanadianDefaultsUseCase
import com.example.mileagetracker.domain.usecase.formatting.FormatCanadianCurrencyUseCase
import com.example.mileagetracker.domain.usecase.localization.GetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.ResetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.SetLanguagePreferenceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for all localization and Canadian-defaults dependencies.
 *
 * ## Scoping notes
 * - [LocalePreferencesDataSource], [LocalePreferencesRepository],
 *   [UserPreferencesDataSource], [UserPreferencesRepository], and
 *   [CanadianCurrencyFormatter] all carry @Singleton + @Inject constructors
 *   and are resolved automatically — no @Provides needed for them.
 *
 * - The five use cases below are provided explicitly because they are
 *   concrete classes without interface bindings and explicit @Provides
 *   keeps the graph readable and easy to swap in tests.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalizationModule {

    // ── Language use cases (Task 6.1) ──────────────────────────────────────

    @Provides
    @Singleton
    fun provideGetLanguagePreferenceUseCase(
        repository: LocalePreferencesRepository,
    ): GetLanguagePreferenceUseCase = GetLanguagePreferenceUseCase(repository)

    @Provides
    @Singleton
    fun provideSetLanguagePreferenceUseCase(
        repository: LocalePreferencesRepository,
    ): SetLanguagePreferenceUseCase = SetLanguagePreferenceUseCase(repository)

    @Provides
    @Singleton
    fun provideResetLanguagePreferenceUseCase(
        repository: LocalePreferencesRepository,
    ): ResetLanguagePreferenceUseCase = ResetLanguagePreferenceUseCase(repository)

    // ── Canadian unit / currency use cases (Task 6.3) ─────────────────────

    @Provides
    @Singleton
    fun provideFormatCanadianCurrencyUseCase(
        formatter: CanadianCurrencyFormatter,
    ): FormatCanadianCurrencyUseCase = FormatCanadianCurrencyUseCase(formatter)

    @Provides
    @Singleton
    fun provideApplyCanadianDefaultsUseCase(
        repository: UserPreferencesRepository,
    ): ApplyCanadianDefaultsUseCase = ApplyCanadianDefaultsUseCase(repository)
}
