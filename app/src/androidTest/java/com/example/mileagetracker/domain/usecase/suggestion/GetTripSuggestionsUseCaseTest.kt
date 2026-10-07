package com.example.mileagetracker.domain.usecase.suggestion

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [GetTripSuggestionsUseCase] against a real in-memory Room DB.
 *
 * These cover the real history query window plus the suggestion grouping and
 * ranking logic on persisted rows rather than mocked collections.
 */
@RunWith(AndroidJUnit4::class)
class GetTripSuggestionsUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepo: DriveRepositoryImpl
    private lateinit var useCase: GetTripSuggestionsUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        useCase = GetTripSuggestionsUseCase(driveRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun candidate(
        startLocation: String = "43.7000,-79.4000",
        endLocation: String = "43.7100,-79.3800",
    ) = DriveEntity(
        startedAt = System.currentTimeMillis(),
        endedAt = System.currentTimeMillis() + HOUR_MS,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.BUSINESS,
        purposeText = "Client Meeting",
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )

    private suspend fun insertHistoryDrive(
        startedAt: Long,
        startLocation: String = "43.7001,-79.4001",
        endLocation: String = "43.7101,-79.3801",
        category: TripCategory = TripCategory.BUSINESS,
        purposeText: String? = "Client Meeting",
        vehicleId: Int? = 7,
        driverUid: String = "u1",
        classificationStatus: ClassificationStatus = ClassificationStatus.USER_CONFIRMED,
    ): Int = driveRepo.saveDrive(
        DriveEntity(
            startedAt = startedAt,
            endedAt = startedAt + HOUR_MS,
            startLocation = startLocation,
            endLocation = endLocation,
            distanceKm = 12f,
            vehicleId = vehicleId,
            driverUid = driverUid,
            tripCategory = category,
            purposeText = purposeText,
            classificationStatus = classificationStatus,
            trackingSource = TrackingSource.AUTO_TRACKED,
        )
    )

    @Test
    fun invoke_noNearbyHistory_returnsEmptyList() = runTest {
        val now = System.currentTimeMillis()
        insertHistoryDrive(
            startedAt = now - DAY_MS,
            startLocation = "45.0000,-75.0000",
            endLocation = "45.1000,-75.1000",
        )

        val suggestions = useCase(candidate(), driverUid = "u1")

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun invoke_recentConfirmedNearbyTrips_returnsGroupedSuggestion() = runTest {
        val now = System.currentTimeMillis()
        val id1 = insertHistoryDrive(startedAt = now - 3 * DAY_MS)
        val id2 = insertHistoryDrive(startedAt = now - 2 * DAY_MS)
        val id3 = insertHistoryDrive(startedAt = now - DAY_MS)

        val suggestions = useCase(candidate(), driverUid = "u1")

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals(TripCategory.BUSINESS, suggestion.suggestedCategory)
        assertEquals("Client Meeting", suggestion.suggestedPurpose)
        assertEquals(7, suggestion.suggestedVehicleId)
        assertEquals(0.15f, suggestion.matchConfidence, 0.0001f)
        assertEquals(listOf(id1, id2, id3), suggestion.basedOnTripIds)
    }

    @Test
    fun invoke_oldPendingAndOtherDriverTrips_areIgnored() = runTest {
        val now = System.currentTimeMillis()
        val id1 = insertHistoryDrive(startedAt = now - 3 * DAY_MS)
        val id2 = insertHistoryDrive(startedAt = now - 2 * DAY_MS)
        val id3 = insertHistoryDrive(startedAt = now - DAY_MS)
        insertHistoryDrive(
            startedAt = now - DAY_MS,
            classificationStatus = ClassificationStatus.PENDING,
        )
        insertHistoryDrive(
            startedAt = now - 120 * DAY_MS,
        )
        insertHistoryDrive(
            startedAt = now - DAY_MS,
            driverUid = "other-driver",
        )

        val suggestions = useCase(candidate(), driverUid = "u1")

        assertEquals(1, suggestions.size)
        assertEquals(listOf(id1, id2, id3), suggestions.single().basedOnTripIds)
        assertEquals(0.15f, suggestions.single().matchConfidence, 0.0001f)
    }

    @Test
    fun invoke_addressFallback_andMultipleGroups_areRankedByConfidence() = runTest {
        val now = System.currentTimeMillis()
        repeat(4) { index ->
            insertHistoryDrive(
                startedAt = now - (index + 1) * DAY_MS,
                startLocation = "123 Main St, Toronto",
                endLocation = "456 Oak Ave, Toronto",
                category = TripCategory.BUSINESS,
                purposeText = "Meeting",
                vehicleId = 1,
            )
        }
        repeat(3) { index ->
            insertHistoryDrive(
                startedAt = now - (index + 10) * DAY_MS,
                startLocation = "123 Main St, Toronto",
                endLocation = "456 Oak Ave, Toronto",
                category = TripCategory.PERSONAL,
                purposeText = "Commute",
                vehicleId = 2,
            )
        }

        val suggestions = useCase(
            candidate(
                startLocation = "123 Main St, Toronto",
                endLocation = "456 Oak Ave, Toronto",
            ),
            driverUid = "u1",
        )

        assertEquals(2, suggestions.size)
        assertEquals(TripCategory.BUSINESS, suggestions[0].suggestedCategory)
        assertEquals("Meeting", suggestions[0].suggestedPurpose)
        assertEquals(0.2f, suggestions[0].matchConfidence, 0.0001f)
        assertEquals(TripCategory.PERSONAL, suggestions[1].suggestedCategory)
        assertEquals("Commute", suggestions[1].suggestedPurpose)
        assertEquals(0.15f, suggestions[1].matchConfidence, 0.0001f)
        assertTrue(suggestions[0].matchConfidence > suggestions[1].matchConfidence)
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
