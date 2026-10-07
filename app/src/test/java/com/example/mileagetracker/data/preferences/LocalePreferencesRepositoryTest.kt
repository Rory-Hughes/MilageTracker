package com.example.mileagetracker.data.preferences

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

class LocalePreferencesRepositoryTest {

    private lateinit var dataSource: LocalePreferencesDataSource
    private lateinit var repository: LocalePreferencesRepository

    @Before
    fun setUp() {
        dataSource = mockk()
        repository = LocalePreferencesRepository(dataSource)
    }

    @Test
    fun `getLanguage maps tag to AppLanguage`() = runTest {
        every { dataSource.languageTag } returns flowOf("fr-CA")
        
        val result = repository.getLanguage().first()
        
        assertEquals(AppLanguage.FRENCH, result)
    }

    @Test
    fun `getLanguage returns system default when tag is null`() = runTest {
        every { dataSource.languageTag } returns flowOf(null)

        val result = repository.getLanguage().first()

        assertEquals(AppLanguage.SYSTEM_DEFAULT, result)
    }

    @Test
    fun `setLanguage clears tag for system default`() = runTest {
        coEvery { dataSource.clearLanguageTag() } returns Unit
        
        repository.setLanguage(AppLanguage.SYSTEM_DEFAULT)
        
        coVerify { dataSource.clearLanguageTag() }
    }

    @Test
    fun `setLanguage updates tag for specific language`() = runTest {
        coEvery { dataSource.setLanguageTag("en") } returns Unit

        repository.setLanguage(AppLanguage.ENGLISH)

        coVerify { dataSource.setLanguageTag("en") }
    }

    @Test
    fun `resetLanguage clears tag in data source`() = runTest {
        coEvery { dataSource.clearLanguageTag() } returns Unit

        repository.resetLanguage()

        coVerify { dataSource.clearLanguageTag() }
    }
}
