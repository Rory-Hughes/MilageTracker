package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.repository.RateRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetCraRateDiagnosticsUseCaseTest {
    private lateinit var rateRepository: RateRepository
    private lateinit var useCase: GetCraRateDiagnosticsUseCase

    @Before
    fun setUp() {
        rateRepository = mockk()
        useCase = GetCraRateDiagnosticsUseCase(rateRepository)
    }

    @Test
    fun `reports seeded and missing years from the CRA catalog`() = runTest {
        coEvery { rateRepository.getSeededYears() } returns listOf(2009, 2010, 2011)

        val diagnostics = useCase()

        assertEquals(LocalDate.now().year, diagnostics.currentYear)
        assertEquals(CraRateCatalog.SOURCE_URL, diagnostics.sourceUrl)
        assertEquals(listOf(2009, 2010, 2011), diagnostics.seededYears)
        assertTrue(diagnostics.unseededYears.contains(2012))
        assertEquals(2009, diagnostics.catalogStartYear)
    }
}
