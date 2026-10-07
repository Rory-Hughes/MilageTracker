package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackfillTripAddressesUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: BackfillTripAddressesUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        resolveDriveLocationsUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns UID
        coEvery { driveRepository.updateDrive(any()) } just Runs
        // Default: real coordinate-shape check delegated to a simple stub.
        every { resolveDriveLocationsUseCase.requiresResolution(any()) } answers {
            val d = firstArg<DriveEntity>()
            d.startLocation.isCoordinate() || d.endLocation.isCoordinate()
        }

        useCase = BackfillTripAddressesUseCase(
            driveRepository,
            resolveDriveLocationsUseCase,
            currentUserProvider,
        )
    }

    @Test
    fun `verbose address is re-resolved from route points and written back`() = runTest {
        val verbose = drive(
            id = 1,
            start = "2638, Westfield Road, Morna, Ketepec, City of Saint John, " +
                "Saint John County, New Brunswick / Nouveau-Brunswick, E2M 6L4, Canada",
            end = "Brunswick Place, Saint John West, City of Saint John, " +
                "Saint John County, New Brunswick / Nouveau-Brunswick, E2K 1C2, Canada",
        )
        every { driveRepository.getAllDrives(UID) } returns flowOf(listOf(verbose))
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns listOf(
            routePoint(45.27347, -66.07600),
            routePoint(45.27312, -66.06000),
        )
        coEvery { resolveDriveLocationsUseCase(any()) } returns verbose.copy(
            startLocation = "2638 Westfield Road, Saint John, NB, Canada",
            endLocation = "Brunswick Place, Saint John, NB, Canada",
        )

        val captured = slot<DriveEntity>()
        coEvery { driveRepository.updateDrive(capture(captured)) } just Runs

        val result = useCase()

        assertTrue(result is BackfillTripAddressesUseCase.Result.Completed)
        assertEquals(1, (result as BackfillTripAddressesUseCase.Result.Completed).updated)
        assertEquals("2638 Westfield Road, Saint John, NB, Canada", captured.captured.startLocation)
        assertEquals("Brunswick Place, Saint John, NB, Canada", captured.captured.endLocation)
    }

    @Test
    fun `already-clean address is not touched`() = runTest {
        val clean = drive(
            id = 1,
            start = "2638 Westfield Road, Saint John, NB, Canada",
            end = "10 Brunswick Place, Saint John, NB, Canada",
        )
        every { driveRepository.getAllDrives(UID) } returns flowOf(listOf(clean))

        val result = useCase()

        assertEquals(0, (result as BackfillTripAddressesUseCase.Result.Completed).scanned)
        coVerify(exactly = 0) { driveRepository.getRoutePointsForDrive(any()) }
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `coordinate-shaped address is re-resolved`() = runTest {
        val coords = drive(id = 1, start = "45.27347, -66.07600", end = "45.27312, -66.06000")
        every { driveRepository.getAllDrives(UID) } returns flowOf(listOf(coords))
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns listOf(
            routePoint(45.27347, -66.07600),
            routePoint(45.27312, -66.06000),
        )
        coEvery { resolveDriveLocationsUseCase(any()) } returns coords.copy(
            startLocation = "A Road, Saint John, NB, Canada",
            endLocation = "B Road, Saint John, NB, Canada",
        )

        val result = useCase()

        assertEquals(1, (result as BackfillTripAddressesUseCase.Result.Completed).updated)
    }

    @Test
    fun `trip without route points is skipped`() = runTest {
        val verbose = drive(
            id = 1,
            start = "X, Y County, New Brunswick / Nouveau-Brunswick, E2M 6L4, Canada",
            end = "Z, Saint John County, New Brunswick, E2K 1C2, Canada",
        )
        every { driveRepository.getAllDrives(UID) } returns flowOf(listOf(verbose))
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns emptyList()

        val result = useCase()

        assertEquals(0, (result as BackfillTripAddressesUseCase.Result.Completed).updated)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `failed lookup never overwrites with bare coordinates`() = runTest {
        val verbose = drive(
            id = 1,
            start = "X, Saint John County, New Brunswick / Nouveau-Brunswick, Canada",
            end = "Y, Saint John County, New Brunswick / Nouveau-Brunswick, Canada",
        )
        every { driveRepository.getAllDrives(UID) } returns flowOf(listOf(verbose))
        coEvery { driveRepository.getRoutePointsForDrive(1) } returns listOf(
            routePoint(45.0, -66.0),
            routePoint(45.1, -66.1),
        )
        // Lookup failed → endpoints remain coordinate strings.
        coEvery { resolveDriveLocationsUseCase(any()) } answers { firstArg() }

        val result = useCase()

        assertEquals(0, (result as BackfillTripAddressesUseCase.Result.Completed).updated)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `no signed-in user yields Skipped`() = runTest {
        every { currentUserProvider.requireUid() } throws IllegalStateException("no user")

        val result = useCase()

        assertTrue(result is BackfillTripAddressesUseCase.Result.Skipped)
        coVerify(exactly = 0) { driveRepository.getAllDrives(any()) }
    }

    private fun String.isCoordinate(): Boolean =
        Regex("^\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*$").matches(this)

    private fun routePoint(lat: Double, lng: Double) = RoutePoint(
        tripId = 0L,
        lat = lat,
        lng = lng,
        altitudeMetres = null,
        accuracyMetres = null,
        speedKph = null,
        timestampMs = 0L,
    )

    private fun drive(id: Int, start: String, end: String) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = start,
        endLocation = end,
        distanceKm = 10f,
        driverUid = UID,
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 0L,
    )

    private companion object {
        const val UID = "driver-1"
    }
}
