package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ApplyCanadianDefaultsUseCaseTest {

    private lateinit var repository: UserPreferencesRepository
    private lateinit var useCase: ApplyCanadianDefaultsUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = ApplyCanadianDefaultsUseCase(repository)
    }

    @Test
    fun `delegates to repository`() = runTest {
        coEvery { repository.applyCanadianDefaultsIfNotSet() } returns Unit
        useCase()
        coVerify { repository.applyCanadianDefaultsIfNotSet() }
    }
}
