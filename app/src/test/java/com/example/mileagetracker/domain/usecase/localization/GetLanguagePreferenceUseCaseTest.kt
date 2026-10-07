package com.example.mileagetracker.domain.usecase.localization

import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.domain.model.AppLanguage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetLanguagePreferenceUseCaseTest {

    private lateinit var repository: LocalePreferencesRepository
    private lateinit var useCase: GetLanguagePreferenceUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetLanguagePreferenceUseCase(repository)
    }

    @Test
    fun `delegates to repository`() = runTest {
        every { repository.getLanguage() } returns flowOf(AppLanguage.FRENCH)
        assertEquals(AppLanguage.FRENCH, useCase().first())
    }
}
