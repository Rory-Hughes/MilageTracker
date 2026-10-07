package com.example.mileagetracker.domain.usecase.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.NoOpSyncRepository
import com.example.mileagetracker.data.repository.TrackedDrivePersistenceRepository
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlushTripBufferUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var tripBufferStore: TripBufferStore
    private lateinit var useCase: FlushTripBufferUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        tripBufferStore = TripBufferStore(db.bufferedTripDao())

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
        val persistTrackedDriveUseCase = PersistTrackedDriveUseCase(
            trackedDrivePersistenceRepository = TrackedDrivePersistenceRepository(
                db = db,
                driveDao = db.driveDao(),
                routePointDao = db.routePointDao(),
                tripAuditDao = db.tripAuditDao(),
                syncDao = db.syncDao(),
                syncRepository = NoOpSyncRepository,
            ),
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
        )

        useCase = FlushTripBufferUseCase(
            tripBufferStore = tripBufferStore,
            persistTrackedDriveUseCase = persistTrackedDriveUseCase,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun invoke_flushesBufferedTrip_intoDrivesRoutePointsAndAudit() = runTest {
        tripBufferStore.enqueue(
            drive(),
            listOf(
                routePoint(lat = 44.6488, lng = -63.5752, timestampMs = 1_000L),
                routePoint(lat = 44.6588, lng = -63.5652, timestampMs = 2_000L),
            ),
        )

        val result = useCase()

        assertEquals(FlushResult(flushed = 1, failed = 0, remaining = 0), result)
        assertEquals(0, db.bufferedTripDao().count())

        val storedDrive = db.driveDao().getAllDrives("u1").first().single()
        assertEquals("Home", storedDrive.startLocation)
        assertEquals("Office", storedDrive.endLocation)
        assertEquals(12.5f, storedDrive.distanceKm)
        assertEquals(TrackingSource.AUTO_TRACKED, storedDrive.trackingSource)
        assertEquals(TripCategory.BUSINESS, storedDrive.tripCategory)

        val storedPoints = db.routePointDao().getPointsForTripOnce(storedDrive.id.toLong())
        assertEquals(2, storedPoints.size)
        assertTrue(storedPoints.all { it.tripId == storedDrive.id.toLong() })
        assertEquals(listOf(1_000L, 2_000L), storedPoints.map { it.timestampMs })

        val auditRows = db.tripAuditDao().getAuditRowsForTripOnce(storedDrive.id)
        assertEquals(1, auditRows.size)
        assertEquals("u1", auditRows.single().actorUid)
        assertEquals(AuditEventType.DRIVE_CREATED, auditRows.single().eventType)
    }

    @Test
    fun invoke_incrementsRetryCount_whenBufferedTripIsCorrupt() = runTest {
        val id = db.bufferedTripDao().insert(
            BufferedTripEntity(
                tripJson = "{",
                routePointsJson = "[]",
                createdAt = 1_000L,
            ),
        )

        val result = useCase()

        assertEquals(FlushResult(flushed = 0, failed = 1, remaining = 1), result)

        val pending = db.bufferedTripDao().getAll()
        assertEquals(1, pending.size)
        assertEquals(id, pending.single().id)
        assertEquals(1, pending.single().retryCount)
    }

    @Test
    fun invoke_prunesExhaustedRows_beforeFlushingHealthyEntries() = runTest {
        db.bufferedTripDao().insert(
            BufferedTripEntity(
                tripJson = "{}",
                routePointsJson = "[]",
                createdAt = 1_000L,
                retryCount = BufferedTripEntity.MAX_RETRY_COUNT,
            ),
        )
        tripBufferStore.enqueue(
            drive(startedAt = 2_000L, distanceKm = 8f),
            listOf(routePoint(lat = 44.7, lng = -63.6, timestampMs = 2_000L)),
        )

        val result = useCase()

        assertEquals(FlushResult(flushed = 1, failed = 0, remaining = 0), result)
        assertEquals(0, db.bufferedTripDao().count())
        assertEquals(1, db.driveDao().getAllDrives("u1").first().size)
    }

    private fun drive(
        startedAt: Long = 1_000L,
        distanceKm: Float = 12.5f,
        driverUid: String = "u1",
    ) = DriveEntity(
        startedAt = startedAt,
        endedAt = startedAt + 1_000L,
        driverUid = driverUid,
        startLocation = "Home",
        endLocation = "Office",
        distanceKm = distanceKm,
        trackingSource = TrackingSource.AUTO_TRACKED,
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
    )

    private fun routePoint(
        lat: Double,
        lng: Double,
        timestampMs: Long,
        altitudeMetres: Double? = null,
        accuracyMetres: Float? = null,
        speedKph: Float? = null,
    ) = RoutePointEntity(
        tripId = 0,
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )
}
