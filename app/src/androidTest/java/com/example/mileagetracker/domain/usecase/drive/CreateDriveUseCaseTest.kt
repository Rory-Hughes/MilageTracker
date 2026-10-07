package com.example.mileagetracker.domain.usecase.drive

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateDriveUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var useCase: CreateDriveUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        val driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        val auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        val resolveDriveLocationsUseCase = ResolveDriveLocationsUseCase(
            tripEndpointAddressRepository = object : TripEndpointAddressRepository {
                override suspend fun reverseGeocodeTripEndpoints(
                    startLat: Double,
                    startLng: Double,
                    endLat: Double,
                    endLng: Double,
                ) = TripEndpointAddresses(
                    startAddress = null,
                    endAddress = null,
                )
            },
        )
        useCase = CreateDriveUseCase(
            driveRepository = driveRepo,
            auditRepository = auditRepo,
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun drive(
        distanceKm: Float = 10f,
        startedAt: Long = 1_000L,
        driverUid: String? = "u1",
    ) = DriveEntity(
        startedAt = startedAt,
        startLocation = "Home",
        endLocation = "Office",
        distanceKm = distanceKm,
        driverUid = driverUid,
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )

    @Test
    fun invoke_negativeDistance_throwsBeforeWrite() = runTest {
        try {
            useCase(drive(distanceKm = -1f), actorUid = "u1")
            fail("Expected IllegalArgumentException for negative distanceKm")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("distance", ignoreCase = true))
        }

        assertTrue(db.driveDao().getAllDrives("u1").first().isEmpty())
    }

    @Test
    fun invoke_zeroDistance_isValid() = runTest {
        val id = useCase(drive(distanceKm = 0f), actorUid = "u1")

        assertTrue(id > 0)
    }

    @Test
    fun invoke_startedAtZero_throws() = runTest {
        try {
            useCase(drive(startedAt = 0L), actorUid = "u1")
            fail("Expected IllegalArgumentException for startedAt = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("startedAt", ignoreCase = true))
        }
    }

    @Test
    fun invoke_blankDriverUid_throws() = runTest {
        try {
            useCase(drive(driverUid = ""), actorUid = "u1")
            fail("Expected IllegalArgumentException for blank driverUid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("driverUid", ignoreCase = true))
        }
    }

    @Test
    fun invoke_nullDriverUid_throws() = runTest {
        try {
            useCase(drive(driverUid = null), actorUid = "u1")
            fail("Expected IllegalArgumentException for null driverUid")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun invoke_validDrive_returnsPositiveId() = runTest {
        val id = useCase(drive(), actorUid = "u1")

        assertTrue(id > 0)
    }

    @Test
    fun invoke_validDrive_driveRowPersistedInDb() = runTest {
        val id = useCase(drive(distanceKm = 12.5f), actorUid = "u1")

        val rows = db.driveDao().getAllDrives("u1").first()
        assertEquals(1, rows.size)
        assertEquals(id, rows.single().id)
        assertEquals(12.5f, rows.single().distanceKm)
    }

    @Test
    fun invoke_writesExactlyOneAuditRow() = runTest {
        useCase(drive(), actorUid = "u1")

        val rows = db.tripAuditDao().getAuditRowsForTripOnce(tripId = 1)
        assertEquals(1, rows.size)
    }

    @Test
    fun invoke_auditRowHasDriveCreatedEventType() = runTest {
        useCase(drive(), actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(tripId = 1).single()
        assertEquals(AuditEventType.DRIVE_CREATED, audit.eventType)
    }

    @Test
    fun invoke_auditRowTripIdMatchesReturnedId() = runTest {
        val id = useCase(drive(), actorUid = "actor-123")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(tripId = id).single()
        assertEquals(id, audit.tripId)
    }

    @Test
    fun invoke_auditRowActorUidMatchesCaller() = runTest {
        useCase(drive(), actorUid = "firebase-uid-xyz")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(tripId = 1).single()
        assertEquals("firebase-uid-xyz", audit.actorUid)
    }

    @Test
    fun invoke_auditRowDiffFieldsAreNull_forDriveCreated() = runTest {
        useCase(drive(), actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(tripId = 1).single()
        assertNull(audit.fieldName)
        assertNull(audit.oldValue)
        assertNull(audit.newValue)
    }

    @Test
    fun invoke_twoDrives_eachHasItsOwnAuditRow() = runTest {
        val id1 = useCase(drive(distanceKm = 5f), actorUid = "u1")
        val id2 = useCase(drive(distanceKm = 8f), actorUid = "u2")

        val audit1 = db.tripAuditDao().getAuditRowsForTripOnce(id1)
        val audit2 = db.tripAuditDao().getAuditRowsForTripOnce(id2)

        assertEquals(1, audit1.size)
        assertEquals(1, audit2.size)
        assertEquals("u1", audit1.single().actorUid)
        assertEquals("u2", audit2.single().actorUid)
    }
}
