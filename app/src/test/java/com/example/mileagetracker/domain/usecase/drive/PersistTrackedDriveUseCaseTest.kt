package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.TrackedDrivePersistenceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PersistTrackedDriveUseCaseTest {

    private lateinit var trackedDrivePersistenceRepository: TrackedDrivePersistenceRepository
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var useCase: PersistTrackedDriveUseCase

    @Before
    fun setUp() {
        trackedDrivePersistenceRepository = mockk()
        resolveDriveLocationsUseCase = mockk()
        coEvery { resolveDriveLocationsUseCase(any()) } answers { firstArg() }
        useCase = PersistTrackedDriveUseCase(
            trackedDrivePersistenceRepository = trackedDrivePersistenceRepository,
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
        )
    }

    @Test
    fun `persists resolved drive route points and audit events`() = runTest {
        val drive = drive()
        val resolvedDrive = drive.copy(
            startLocation = "123 Main St",
            endLocation = "456 Queen St",
        )
        val routePoints = listOf(routePoint())
        val extraAudit = listOf(
            TrackedDriveAuditEvent(
                eventType = AuditEventType.DUPLICATE_FLAGGED,
                fieldName = "existing_trip_id",
                oldValue = "7",
                newValue = "82%",
            )
        )
        coEvery { resolveDriveLocationsUseCase(drive) } returns resolvedDrive
        coEvery {
            trackedDrivePersistenceRepository.persistTrackedDrive(
                resolvedDrive,
                routePoints,
                "u1",
                extraAudit,
            )
        } returns 42

        val result = useCase(drive, routePoints, "u1", extraAudit)

        assertEquals(42, result)
        coVerify {
            trackedDrivePersistenceRepository.persistTrackedDrive(
                resolvedDrive,
                routePoints,
                "u1",
                extraAudit,
            )
        }
    }

    @Test
    fun `does not resolve or persist when validation fails`() = runTest {
        try {
            useCase(drive().copy(driverUid = " "), emptyList(), "u1")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        coVerify(exactly = 0) { resolveDriveLocationsUseCase(any()) }
        coVerify(exactly = 0) {
            trackedDrivePersistenceRepository.persistTrackedDrive(any(), any(), any(), any())
        }
    }

    private fun drive() = DriveEntity(
        startedAt = 1_000L,
        endedAt = 2_000L,
        driverUid = "u1",
        startLocation = "44.6488, -63.5752",
        endLocation = "44.6713, -63.6103",
        distanceKm = 8.5f,
        trackingSource = TrackingSource.AUTO_TRACKED,
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
    )

    private fun routePoint() = RoutePointEntity(
        tripId = 0,
        lat = 44.6488,
        lng = -63.5752,
        altitudeMetres = null,
        accuracyMetres = null,
        speedKph = null,
        timestampMs = 1_000L,
    )
}
