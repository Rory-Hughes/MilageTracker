package com.example.mileagetracker.domain.usecase.localization

import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LanguagePreferenceUseCasesTest {

    private lateinit var repository: LocalePreferencesRepository
    private lateinit var getUseCase: GetLanguagePreferenceUseCase
    private lateinit var setUseCase: SetLanguagePreferenceUseCase
    private lateinit var resetUseCase: ResetLanguagePreferenceUseCase

    @Before
    fun setUp() {
        repository = mockk()
        getUseCase = GetLanguagePreferenceUseCase(repository)
        setUseCase = SetLanguagePreferenceUseCase(repository)
        resetUseCase = ResetLanguagePreferenceUseCase(repository)
    }

    @Test
    fun `GetLanguagePreferenceUseCase delegates to repository`() = runTest {
        every { repository.getLanguage() } returns flowOf(AppLanguage.FRENCH)
        assertEquals(AppLanguage.FRENCH, getUseCase().first())
    }

    @Test
    fun `SetLanguagePreferenceUseCase delegates to repository`() = runTest {
        coEvery { repository.setLanguage(AppLanguage.ENGLISH) } returns Unit
        setUseCase(AppLanguage.ENGLISH)
        coVerify { repository.setLanguage(AppLanguage.ENGLISH) }
    }

    @Test
    fun `ResetLanguagePreferenceUseCase delegates to repository`() = runTest {
        coEvery { repository.resetLanguage() } returns Unit
        resetUseCase()
        coVerify { repository.resetLanguage() }
    }
}
