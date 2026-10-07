package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus.Companion.MIN_BASE_YEAR_TRIPS
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class CheckSimplifiedLogbookEligibilityUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: CheckSimplifiedLogbookEligibilityUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        settingsRepository = mockk()
        useCase = CheckSimplifiedLogbookEligibilityUseCase(driveRepository, settingsRepository)
    }

    private fun testDrive(km: Float, category: TripCategory = TripCategory.BUSINESS) = DriveEntity(
        id = 0,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    @Test
    fun `returns ineligible if no base year has enough trips`() = runTest {
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns emptyList()

        val status = useCase("u1")

        assertFalse(status.isEligible)
        assertEquals(0.0, status.currentSampleKm, 0.01)
    }

    @Test
    fun `returns eligible if base year meets trip count`() = runTest {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val baseYear = currentYear - 1
        
        // Mock base year with exactly MIN_BASE_YEAR_TRIPS
        val baseYearDrives = List(MIN_BASE_YEAR_TRIPS) { testDrive(10f) }
        
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } answers {
            val from = it.invocation.args[1] as Long
            val cal = Calendar.getInstance().apply { timeInMillis = from }
            if (cal.get(Calendar.YEAR) == baseYear) baseYearDrives else emptyList()
        }

        val status = useCase("u1")

        assertTrue(status.isEligible)
        assertEquals(baseYear, status.baseYear)
        assertEquals(100.0, status.baseYearBusinessUsePercent!!, 0.01)
    }

    @Test
    fun `calculates sample progress correctly`() = runTest {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val baseYear = currentYear - 1
        
        // Base year: 100 trips * 10km = 1000km business.
        // Required sample: 25% of 1000km = 250km.
        val baseYearDrives = List(100) { testDrive(10f) }
        
        // Current year: 125km business (50% of required).
        val currentYearDrives = List(5) { testDrive(25f) }

        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } answers {
            val from = it.invocation.args[1] as Long
            val cal = Calendar.getInstance().apply { timeInMillis = from }
            when (cal.get(Calendar.YEAR)) {
                baseYear -> baseYearDrives
                currentYear -> currentYearDrives
                else -> emptyList()
            }
        }

        val status = useCase("u1")

        assertTrue(status.isEligible)
        assertEquals(125.0, status.currentSampleKm, 0.01)
        assertEquals(250.0, status.requiredSampleKm, 0.01)
        assertEquals(50.0, status.sampleProgressPercent, 0.01)
    }
}
