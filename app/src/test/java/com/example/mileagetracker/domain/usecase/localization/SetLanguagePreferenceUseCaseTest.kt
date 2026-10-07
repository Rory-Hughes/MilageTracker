package com.example.mileagetracker.domain.usecase.localization

import androidx.appcompat.app.AppCompatDelegate
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class SetLanguagePreferenceUseCaseTest {

    private lateinit var repository: LocalePreferencesRepository
    private lateinit var useCase: SetLanguagePreferenceUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SetLanguagePreferenceUseCase(repository)
        mockkStatic(AppCompatDelegate::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
    }

    @Test
    fun `delegates to repository and sets app locales`() = runTest {
        coEvery { repository.setLanguage(AppLanguage.FRENCH) } returns Unit
        
        useCase(AppLanguage.FRENCH)
        
        coVerify { repository.setLanguage(AppLanguage.FRENCH) }
        coVerify { AppCompatDelegate.setApplicationLocales(any()) }
    }
}
