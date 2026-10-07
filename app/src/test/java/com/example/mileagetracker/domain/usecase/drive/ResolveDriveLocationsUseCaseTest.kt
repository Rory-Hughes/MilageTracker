package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveDriveLocationsUseCaseTest {

    private lateinit var repository: TripEndpointAddressRepository
    private lateinit var useCase: ResolveDriveLocationsUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = ResolveDriveLocationsUseCase(repository)
    }

    @Test
    fun `requiresResolution returns true when either endpoint is a coordinate string`() {
        val drive = drive(
            startLocation = "44.6488, -63.5752",
            endLocation = "Office",
        )

        assertTrue(useCase.requiresResolution(drive))
    }

    @Test
    fun `requiresResolution returns false for readable locations`() {
        val drive = drive(
            startLocation = "Halifax Waterfront",
            endLocation = "Mumford Terminal",
        )

        assertFalse(useCase.requiresResolution(drive))
    }

    @Test
    fun `invoke resolves coordinate endpoints and normalizes postal codes`() = runTest {
        val drive = drive(
            startLocation = "44.6488, -63.5752",
            endLocation = "44.6713, -63.6103",
        )
        coEvery {
            repository.reverseGeocodeTripEndpoints(
                startLat = 44.6488,
                startLng = -63.5752,
                endLat = 44.6713,
                endLng = -63.6103,
            )
        } returns TripEndpointAddresses(
            startAddress = "1800 Lower Water St, Halifax, NS b3j3r8",
            endAddress = "6960 Mumford Rd, Halifax, NS b3l4p1",
        )

        val resolved = useCase(drive)

        assertEquals("1800 Lower Water St, Halifax, NS B3J 3R8", resolved.startLocation)
        assertEquals("6960 Mumford Rd, Halifax, NS B3L 4P1", resolved.endLocation)
    }

    @Test
    fun `invoke leaves readable locations untouched without calling repository`() = runTest {
        val drive = drive(
            startLocation = "Halifax Waterfront",
            endLocation = "Mumford Terminal",
        )

        val resolved = useCase(drive)

        assertEquals(drive, resolved)
        coVerify(exactly = 0) { repository.reverseGeocodeTripEndpoints(any(), any(), any(), any()) }
    }

    @Test
    fun `invoke falls back to original drive when lookup fails`() = runTest {
        val drive = drive(
            startLocation = "44.6488, -63.5752",
            endLocation = "44.6713, -63.6103",
        )
        coEvery {
            repository.reverseGeocodeTripEndpoints(any(), any(), any(), any())
        } throws RuntimeException("boom")

        val resolved = useCase(drive)

        assertEquals(drive, resolved)
    }

    private fun drive(
        startLocation: String,
        endLocation: String,
    ) = DriveEntity(
        id = 1,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
    )
}
