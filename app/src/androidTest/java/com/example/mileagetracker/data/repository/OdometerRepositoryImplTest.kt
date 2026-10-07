package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [OdometerRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * Beyond what [OdometerCaptureDaoTest] already exercises, these tests verify:
 *
 * - **Domain mapping**: [OdometerCaptureEntity] fields are correctly mapped to
 *   [OdometerCapture] domain objects, including the [OdometerCaptureType]
 *   string-enum conversion.
 * - **`runCatching` fallback**: an entity with an unrecognised `captureType`
 *   string (possible if a new enum value is rolled back in a hotfix) falls
 *   back to [OdometerCaptureType.MANUAL] rather than crashing.
 * - **Year boundary queries**: [getFirstCaptureForVehicleInYear] and
 *   [getLastCaptureForVehicleInYear] return domain objects with the correct
 *   field values.
 *
 * The repository is wired directly (no Hilt) with the real DAO. Photo-copying
 * paths that require a ContentResolver are not exercised here; all test
 * captures use `photoUri = null`.
 *
 * `odometer_captures.vehicle_id` is a hard FK to `vehicles.id`, so a parent
 * vehicle is inserted in [setUp].
 */
@RunWith(AndroidJUnit4::class)
class OdometerRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: OdometerRepositoryImpl
    private var vehicleId: Int = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = OdometerRepositoryImpl(
            dao = db.odometerCaptureDao(),
            context = ApplicationProvider.getApplicationContext(),
        )

        vehicleId = db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid = "u1",
                displayName = "Test Car",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun capture(
        captureDate: Long,
        readingKm: Int = 10_000,
        captureType: OdometerCaptureType = OdometerCaptureType.MANUAL,
        notes: String? = null,
        photoUri: String? = null,
    ) = OdometerCapture(
        vehicleId = vehicleId,
        captureDate = captureDate,
        readingKm = readingKm,
        captureType = captureType,
        notes = notes,
        photoUri = photoUri,
    )

    // ── saveCapture: domain model mapping ─────────────────────────────────────

    @Test
    fun saveCapture_returnsCapture_withAssignedId_andAllFieldsRoundTrip() = runTest {
        val saved = repository.saveCapture(
            capture(
                captureDate = 5_000L,
                readingKm = 12_345,
                captureType = OdometerCaptureType.PHOTO_ASSISTED,
                notes = "Annual close",
                photoUri = null,
            ),
        )

        assertTrue(saved.id > 0)
        assertEquals(vehicleId, saved.vehicleId)
        assertEquals(5_000L, saved.captureDate)
        assertEquals(12_345, saved.readingKm)
        assertEquals(OdometerCaptureType.PHOTO_ASSISTED, saved.captureType)
        assertEquals("Annual close", saved.notes)
    }

    @Test
    fun saveCapture_withNullPhotoUri_persistsNullAndMapsBack() = runTest {
        val saved = repository.saveCapture(capture(captureDate = 1_000L, photoUri = null))
        assertNull(saved.photoUri)
    }

    // ── observeCapturesForVehicle ─────────────────────────────────────────────

    @Test
    fun observeCapturesForVehicle_emitsMappedDomainObjects_newestFirst() = runTest {
        repository.saveCapture(capture(captureDate = 1_000L, readingKm = 100))
        repository.saveCapture(capture(captureDate = 3_000L, readingKm = 300))
        repository.saveCapture(capture(captureDate = 2_000L, readingKm = 200))

        val captures = repository.observeCapturesForVehicle(vehicleId).first()

        assertEquals(3, captures.size)
        // DESC by capture_date
        assertEquals(listOf(3_000L, 2_000L, 1_000L), captures.map { it.captureDate })
        assertEquals(listOf(300, 200, 100), captures.map { it.readingKm })
        assertTrue(captures.all { it.vehicleId == vehicleId })
    }

    @Test
    fun observeCapturesForVehicle_returnsEmpty_forVehicleWithNoCaptures() = runTest {
        assertTrue(repository.observeCapturesForVehicle(vehicleId).first().isEmpty())
    }

    // ── getLatestReading ──────────────────────────────────────────────────────

    @Test
    fun getLatestReading_returnsCapture_withHighestCaptureDate() = runTest {
        repository.saveCapture(capture(captureDate = 1_000L, readingKm = 100))
        repository.saveCapture(capture(captureDate = 3_000L, readingKm = 300))
        repository.saveCapture(capture(captureDate = 2_000L, readingKm = 200))

        val latest = repository.getLatestReading(vehicleId)!!
        assertEquals(3_000L, latest.captureDate)
        assertEquals(300, latest.readingKm)
    }

    @Test
    fun getLatestReading_returnsNull_forVehicleWithNoCaptures() = runTest {
        assertNull(repository.getLatestReading(vehicleId))
    }

    // ── getFirstCaptureForVehicleInYear (opening odometer) ────────────────────

    @Test
    fun getFirstCaptureForVehicleInYear_returnsEarliestInWindow_asDomainObject() = runTest {
        repository.saveCapture(capture(captureDate = 999L,   readingKm = 1))   // before window
        repository.saveCapture(capture(captureDate = 1_000L, readingKm = 10))  // at lower bound
        repository.saveCapture(capture(captureDate = 2_000L, readingKm = 20))
        repository.saveCapture(capture(captureDate = 3_001L, readingKm = 40))  // after window

        val first = repository.getFirstCaptureForVehicleInYear(
            vehicleId = vehicleId, fromMillis = 1_000L, toMillis = 3_000L,
        )!!

        assertEquals(1_000L, first.captureDate)
        assertEquals(10, first.readingKm)
        assertEquals(vehicleId, first.vehicleId)
    }

    @Test
    fun getFirstCaptureForVehicleInYear_returnsNull_whenNoCaptures() = runTest {
        assertNull(
            repository.getFirstCaptureForVehicleInYear(
                vehicleId = vehicleId, fromMillis = 1_000L, toMillis = 2_000L,
            ),
        )
    }

    // ── getLastCaptureForVehicleInYear (closing odometer) ─────────────────────

    @Test
    fun getLastCaptureForVehicleInYear_returnsLatestInWindow_asDomainObject() = runTest {
        repository.saveCapture(capture(captureDate = 999L,   readingKm = 1))
        repository.saveCapture(capture(captureDate = 1_000L, readingKm = 10))
        repository.saveCapture(capture(captureDate = 3_000L, readingKm = 30)) // should be returned
        repository.saveCapture(capture(captureDate = 3_001L, readingKm = 40)) // after window

        val last = repository.getLastCaptureForVehicleInYear(
            vehicleId = vehicleId, fromMillis = 1_000L, toMillis = 3_000L,
        )!!

        assertEquals(3_000L, last.captureDate)
        assertEquals(30, last.readingKm)
    }

    // ── toDomain runCatching fallback ─────────────────────────────────────────

    @Test
    fun toDomain_unknownCaptureTypeString_fallsBackToManual() = runTest {
        // Insert a raw entity with an unrecognised captureType string directly via
        // the DAO — simulates a legacy row from a rolled-back hotfix.
        db.odometerCaptureDao().insert(
            OdometerCaptureEntity(
                vehicleId = vehicleId,
                captureDate = 7_000L,
                readingKm = 50_000,
                captureType = "LEGACY_PHOTO",  // not a valid OdometerCaptureType
                createdAt = 7_000L,
            ),
        )

        // The repository must not throw; unknown type resolves to MANUAL.
        val latest = repository.getLatestReading(vehicleId)!!
        assertEquals(OdometerCaptureType.MANUAL, latest.captureType)
        assertEquals(50_000, latest.readingKm)
    }
}
