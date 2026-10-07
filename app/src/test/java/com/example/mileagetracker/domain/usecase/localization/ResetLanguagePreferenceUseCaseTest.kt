package com.example.mileagetracker.domain.usecase.localization

import androidx.appcompat.app.AppCompatDelegate
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ResetLanguagePreferenceUseCaseTest {

    private lateinit var repository: LocalePreferencesRepository
    private lateinit var useCase: ResetLanguagePreferenceUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = ResetLanguagePreferenceUseCase(repository)
        mockkStatic(AppCompatDelegate::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
    }

    @Test
    fun `delegates to repository and resets app locales`() = runTest {
        coEvery { repository.resetLanguage() } returns Unit
        
        useCase()
        
        coVerify { repository.resetLanguage() }
        coVerify { AppCompatDelegate.setApplicationLocales(any()) }
    }
}
