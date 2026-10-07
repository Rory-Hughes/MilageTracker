package com.example.mileagetracker.domain.usecase.suggestion

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetTripSuggestionsUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var useCase: GetTripSuggestionsUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        useCase = GetTripSuggestionsUseCase(driveRepository)
    }

    private fun testDrive(
        id: Int = 1,
        start: String = "0.0,0.0",
        end: String = "0.1,0.1",
        category: TripCategory = TripCategory.BUSINESS,
        status: ClassificationStatus = ClassificationStatus.USER_CONFIRMED,
        purpose: String? = "Meeting"
    ) = DriveEntity(
        id = id,
        startedAt = System.currentTimeMillis() - 1000,
        endedAt = System.currentTimeMillis(),
        startLocation = start,
        endLocation = end,
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = category,
        purposeText = purpose,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = System.currentTimeMillis()
    )

    @Test
    fun `returns empty list when no history`() = runTest {
        coEvery { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) } returns emptyList()
        
        val candidate = testDrive(id = 0)
        val suggestions = useCase(candidate, "u1")
        
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `returns suggestion when matching history found`() = runTest {
        val history = listOf(
            testDrive(id = 1, category = TripCategory.BUSINESS, purpose = "Meeting"),
            testDrive(id = 2, category = TripCategory.BUSINESS, purpose = "Meeting"),
            testDrive(id = 3, category = TripCategory.BUSINESS, purpose = "Meeting"),
            testDrive(id = 4, category = TripCategory.BUSINESS, purpose = "Meeting")
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns history
        
        val candidate = testDrive(id = 0) // Defaults to same locations
        val suggestions = useCase(candidate, "u1")
        
        assertEquals(1, suggestions.size)
        assertEquals(TripCategory.BUSINESS, suggestions[0].suggestedCategory)
        assertEquals("Meeting", suggestions[0].suggestedPurpose)
        // matchConfidence = 4 / 20 = 0.2
        assertEquals(0.2f, suggestions[0].matchConfidence, 0.01f)
    }

    @Test
    fun `suggests multiple groups sorted by confidence`() = runTest {
        val history = mutableListOf<DriveEntity>()
        repeat(5) { history.add(testDrive(id = it, category = TripCategory.BUSINESS, purpose = "B")) }
        repeat(3) { history.add(testDrive(id = it + 10, category = TripCategory.PERSONAL, purpose = "P")) }
        
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns history
        
        val candidate = testDrive(id = 0)
        val suggestions = useCase(candidate, "u1")
        
        assertEquals(2, suggestions.size)
        assertEquals(TripCategory.BUSINESS, suggestions[0].suggestedCategory)
        assertEquals(TripCategory.PERSONAL, suggestions[1].suggestedCategory)
        assertTrue(suggestions[0].matchConfidence > suggestions[1].matchConfidence)
    }

    @Test
    fun `falls back to address similarity if lat-lng parsing fails`() = runTest {
        val historyTrips = listOf(
            testDrive(id = 1, start = "123 Main St, Toronto", end = "456 Oak Ave, Toronto"),
            testDrive(id = 2, start = "123 Main St, Toronto", end = "456 Oak Ave, Toronto"),
            testDrive(id = 3, start = "123 Main St, Toronto", end = "456 Oak Ave, Toronto"),
        )
        coEvery { driveRepository.getDrivesForAnnualSummary("u1", any(), any()) } returns historyTrips
        
        // Candidate with identical strings but not lat-lng
        val candidate = testDrive(id = 0, start = "123 Main St, Toronto", end = "456 Oak Ave, Toronto")
        val suggestions = useCase(candidate, "u1")
        
        assertEquals(1, suggestions.size)
    }
}
