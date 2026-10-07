package com.example.mileagetracker.data.preferences

import com.example.mileagetracker.domain.model.CanadianUnits
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UserPreferencesRepositoryTest {

    private lateinit var dataSource: UserPreferencesDataSource
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        dataSource = mockk(relaxed = true)
        repository = UserPreferencesRepository(dataSource)
    }

    @Test
    fun `applyCanadianDefaultsIfNotSet applies defaults if not already applied`() = runTest {
        every { dataSource.canadianDefaultsApplied } returns flowOf(false)

        repository.applyCanadianDefaultsIfNotSet()

        coVerify {
            dataSource.setDistanceUnit(CanadianUnits.DISTANCE_UNIT)
            dataSource.setCurrencyCode(CanadianUnits.CURRENCY_CODE)
            dataSource.markCanadianDefaultsApplied()
        }
    }

    @Test
    fun `applyCanadianDefaultsIfNotSet does nothing if already applied`() = runTest {
        every { dataSource.canadianDefaultsApplied } returns flowOf(true)

        repository.applyCanadianDefaultsIfNotSet()

        coVerify(exactly = 0) { dataSource.setDistanceUnit(any()) }
        coVerify(exactly = 0) { dataSource.setCurrencyCode(any()) }
    }

    @Test
    fun `setDistanceUnit delegates to dataSource`() = runTest {
        repository.setDistanceUnit("miles")
        coVerify { dataSource.setDistanceUnit("miles") }
    }

    @Test
    fun `setCurrencyCode delegates to dataSource`() = runTest {
        repository.setCurrencyCode("USD")
        coVerify { dataSource.setCurrencyCode("USD") }
    }

    @Test
    fun `distanceUnit and currencyCode flows sourced from dataSource`() = runTest {
        every { dataSource.distanceUnit } returns flowOf("km")
        every { dataSource.currencyCode } returns flowOf("CAD")
        // Repository captures dataSource.distanceUnit/currencyCode in its constructor, so create
        // a fresh instance after the mocks are in place.
        val repo = UserPreferencesRepository(dataSource)

        assertEquals("km", repo.distanceUnit.first())
        assertEquals("CAD", repo.currencyCode.first())
    }
}
