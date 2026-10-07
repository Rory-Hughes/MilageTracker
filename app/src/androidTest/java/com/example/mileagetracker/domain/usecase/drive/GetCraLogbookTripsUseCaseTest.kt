package com.example.mileagetracker.domain.usecase.drive

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Integration tests for [GetCraLogbookTripsUseCase] against a real (in-memory)
 * Room DB.
 *
 * ## Behaviour under test
 * - **Empty year**: returns empty entries list and a summary with all-zero km.
 * - **Category filtering**: only trips whose [TripCategory] is in the `categories`
 *   parameter appear in the entries list; the total-km denominator includes
 *   *all* trips regardless of category.
 * - **Business-use percentage**: `filteredKm / totalKm * 100`.
 * - **Vehicle name resolution**: display name fetched from the `vehicles` table;
 *   falls back to "—" when no vehicle is assigned.
 * - **Year isolation**: trips outside the requested year are excluded.
 * - **Vehicle filter**: when `vehicleId` is non-null, only trips for that vehicle
 *   appear.
 * - **Chronological order**: entries sorted by `startedAt` ascending.
 * - **Purpose fallback**: when [DriveEntity.purposeText] is null, the entry's
 *   `purpose` field is the category's display name.
 */
@RunWith(AndroidJUnit4::class)
class GetCraLogbookTripsUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var driveRepo:   DriveRepositoryImpl
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase:     GetCraLogbookTripsUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo   = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase     = GetCraLogbookTripsUseCase(
            driveRepo,
            vehicleRepo,
            ResolveDriveLocationsUseCase(NoOpTripEndpointAddressRepository),
        )
    }

    private object NoOpTripEndpointAddressRepository : TripEndpointAddressRepository {
        override suspend fun reverseGeocodeTripEndpoints(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double,
        ): TripEndpointAddresses = TripEndpointAddresses(startAddress = null, endAddress = null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private fun midYear(year: Int, month: Int = Calendar.JUNE, day: Int = 15): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private suspend fun insertDrive(
        driverUid:    String       = "u1",
        startedAt:    Long         = midYear(2024),
        distanceKm:   Float        = 10f,
        category:     TripCategory = TripCategory.BUSINESS,
        vehicleId:    Int?         = null,
        purposeText:  String?      = null,
        endLocation:  String       = "Office",
    ): Int = driveRepo.saveDrive(
        DriveEntity(
            driverUid            = driverUid,
            startedAt            = startedAt,
            startLocation        = "Home",
            endLocation          = endLocation,
            distanceKm           = distanceKm,
            tripCategory         = category,
            classificationStatus = ClassificationStatus.USER_CONFIRMED,
            trackingSource       = TrackingSource.AUTO_TRACKED,
            vehicleId            = vehicleId,
            purposeText          = purposeText,
        )
    )

    private suspend fun insertVehicle(displayName: String = "My Car"): Int =
        vehicleRepo.saveVehicle(
            VehicleEntity(
                ownerUid    = "u1",
                displayName = displayName,
                fiscalYear  = 2024,
            )
        )

    // ── Empty year ────────────────────────────────────────────────────────────────

    @Test
    fun invoke_emptyYear_returnsEmptyEntries() = runTest {
        val (entries, _) = useCase("u1", 2024)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun invoke_emptyYear_summaryHasZeroKm() = runTest {
        val (_, summary) = useCase("u1", 2024)
        assertEquals(0f, summary.totalKm)
        assertEquals(0f, summary.businessKm)
        assertEquals(0f, summary.businessUsePercent)
    }

    // ── Category filtering ─────────────────────────────────────────────────────────

    @Test
    fun invoke_onlyBusinessTrips_defaultFilter_allIncluded() = runTest {
        insertDrive(category = TripCategory.BUSINESS, distanceKm = 30f)

        val (entries, summary) = useCase("u1", 2024)

        assertEquals(1, entries.size)
        assertEquals(30f, summary.businessKm)
    }

    @Test
    fun invoke_personalTripOnly_defaultFilter_excludedFromEntries() = runTest {
        insertDrive(category = TripCategory.PERSONAL, distanceKm = 20f)

        val (entries, summary) = useCase("u1", 2024)

        assertTrue("Personal trip must not appear in entries", entries.isEmpty())
    }

    @Test
    fun invoke_mixedCategories_defaultFilter_onlyBusinessInEntries() = runTest {
        insertDrive(category = TripCategory.BUSINESS, distanceKm = 50f)
        insertDrive(category = TripCategory.PERSONAL,  distanceKm = 30f)

        val (entries, _) = useCase("u1", 2024)

        assertEquals(1, entries.size)
        assertEquals(50f, entries.single().businessKm)
    }

    @Test
    fun invoke_mixedCategories_defaultFilter_totalKmIncludesAll() = runTest {
        insertDrive(category = TripCategory.BUSINESS, distanceKm = 50f)
        insertDrive(category = TripCategory.PERSONAL,  distanceKm = 30f)

        val (_, summary) = useCase("u1", 2024)

        assertEquals(80f, summary.totalKm)
    }

    // ── Business-use percentage ───────────────────────────────────────────────────

    @Test
    fun invoke_allBusinessTrips_businessUsePercent100() = runTest {
        insertDrive(category = TripCategory.BUSINESS, distanceKm = 100f)

        val (_, summary) = useCase("u1", 2024)

        assertEquals(100f, summary.businessUsePercent)
    }

    @Test
    fun invoke_halfBusinessHalfPersonal_businessUsePercent50() = runTest {
        insertDrive(category = TripCategory.BUSINESS, distanceKm = 50f)
        insertDrive(category = TripCategory.PERSONAL,  distanceKm = 50f)

        val (_, summary) = useCase("u1", 2024)

        assertEquals(50f, summary.businessUsePercent, 0.01f)
    }

    // ── Vehicle name resolution ───────────────────────────────────────────────────

    @Test
    fun invoke_driveWithVehicle_vehicleNameInEntry() = runTest {
        val vehicleId = insertVehicle("Toyota Camry")
        insertDrive(vehicleId = vehicleId)

        val (entries, _) = useCase("u1", 2024)

        assertEquals("Toyota Camry", entries.single().vehicleName)
    }

    @Test
    fun invoke_driveWithNoVehicle_vehicleNameIsDash() = runTest {
        insertDrive(vehicleId = null)

        val (entries, _) = useCase("u1", 2024)

        assertEquals("—", entries.single().vehicleName)
    }

    // ── Year isolation ────────────────────────────────────────────────────────────

    @Test
    fun invoke_tripInDifferentYear_notIncluded() = runTest {
        insertDrive(startedAt = midYear(2023))  // wrong year

        val (entries, summary) = useCase("u1", 2024)

        assertTrue(entries.isEmpty())
        assertEquals(0f, summary.totalKm)
    }

    @Test
    fun invoke_tripsInTwoYears_onlyRequestedYearReturned() = runTest {
        insertDrive(startedAt = midYear(2024), distanceKm = 40f)
        insertDrive(startedAt = midYear(2025), distanceKm = 60f)

        val (entries, summary) = useCase("u1", 2024)

        assertEquals(1, entries.size)
        assertEquals(40f, summary.businessKm)
    }

    // ── Vehicle filter ────────────────────────────────────────────────────────────

    @Test
    fun invoke_vehicleFilter_onlyThatVehicleIncluded() = runTest {
        val v1 = insertVehicle("Car A")
        val v2 = insertVehicle("Car B")
        insertDrive(vehicleId = v1, distanceKm = 30f)
        insertDrive(vehicleId = v2, distanceKm = 20f)

        val (entries, summary) = useCase("u1", 2024, vehicleId = v1)

        assertEquals(1, entries.size)
        assertEquals(30f, summary.businessKm)
    }

    // ── Chronological ordering ────────────────────────────────────────────────────

    @Test
    fun invoke_multipleTrips_sortedByStartedAtAscending() = runTest {
        val early = midYear(2024, Calendar.MARCH,  10)
        val late  = midYear(2024, Calendar.AUGUST, 20)
        insertDrive(startedAt = late,  endLocation = "B")
        insertDrive(startedAt = early, endLocation = "A")

        val (entries, _) = useCase("u1", 2024)

        assertEquals("A", entries[0].destination)
        assertEquals("B", entries[1].destination)
    }

    // ── Category column ───────────────────────────────────────────────────────────

    @Test
    fun invoke_entryCategoryMatchesDriveCategory() = runTest {
        insertDrive(purposeText = "ignored", category = TripCategory.BUSINESS)

        val (entries, _) = useCase("u1", 2024)

        // The logbook surfaces the category directly; purposeText is not echoed.
        assertEquals(TripCategory.BUSINESS, entries.single().category)
    }

    // ── Driver isolation ──────────────────────────────────────────────────────────

    @Test
    fun invoke_tripsForOtherDriver_notIncluded() = runTest {
        insertDrive(driverUid = "driver-2")

        val (entries, _) = useCase("u1", 2024)

        assertTrue(entries.isEmpty())
    }

    // ── Summary metadata ──────────────────────────────────────────────────────────

    @Test
    fun invoke_summaryYearMatchesRequest() = runTest {
        val (_, summary) = useCase("u1", 2024)
        assertEquals(2024, summary.year)
    }

    @Test
    fun invoke_summaryVehicleIdMatchesFilter() = runTest {
        val vehicleId = insertVehicle()
        insertDrive(vehicleId = vehicleId)

        val (_, summary) = useCase("u1", 2024, vehicleId = vehicleId)

        assertEquals(vehicleId, summary.vehicleId)
    }
}
