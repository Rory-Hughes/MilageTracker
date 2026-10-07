package com.example.mileagetracker.domain.usecase.odometer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.OdometerRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.model.OdometerCapture
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SaveOdometerCaptureUseCase] against a real (in-memory)
 * Room DB.
 *
 * [OdometerRepositoryImpl] is wired with a real [ApplicationContext] so that the
 * file-copy path compiles; all test captures omit a photo URI, so no actual
 * file I/O is triggered.
 *
 * ## Behaviour under test
 * - **Invalid reading (≤ 0)**: returns [OdometerSaveResult.InvalidReading] without
 *   writing to the database.
 * - **Valid first reading**: returns [OdometerSaveResult.Success] with a positive id.
 * - **Regression** (new reading < previous): returns
 *   [OdometerSaveResult.RegressionWarning] with the previous reading; the row is
 *   still persisted.
 * - **Progression** (new reading ≥ previous): returns [OdometerSaveResult.Success].
 * - **Multiple captures**: each gets a distinct id; the latest-reading query
 *   used for regression detection sees the most-recent value.
 */
@RunWith(AndroidJUnit4::class)
class SaveOdometerCaptureUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var odometerRepo: OdometerRepositoryImpl
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase: SaveOdometerCaptureUseCase

    private var vehicleId: Int = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        odometerRepo = OdometerRepositoryImpl(
            dao = db.odometerCaptureDao(),
            context = ApplicationProvider.getApplicationContext(),
        )
        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase = SaveOdometerCaptureUseCase(odometerRepo)

        // All tests share one vehicle
        vehicleId = 0 // reset; actual value set in setUp suspend or first test call
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun ensureVehicle(): Int {
        if (vehicleId == 0) {
            vehicleId = vehicleRepo.saveVehicle(
                VehicleEntity(
                    ownerUid = "u1",
                    displayName = "Test Car",
                    fiscalYear = 2026,
                )
            )
        }
        return vehicleId
    }

    private suspend fun capture(readingKm: Int, vid: Int? = null) = OdometerCapture(
        vehicleId = vid ?: ensureVehicle(),
        captureDate = 1_700_000_000_000L,
        readingKm = readingKm,
    )

    // ── Invalid reading ───────────────────────────────────────────────────────────

    @Test
    fun invoke_zeroReading_returnsInvalidReading() = runTest {
        val result = useCase(capture(readingKm = 0))

        assertTrue(result is OdometerSaveResult.InvalidReading)
    }

    @Test
    fun invoke_negativeReading_returnsInvalidReading() = runTest {
        val result = useCase(capture(readingKm = -100))

        assertTrue(result is OdometerSaveResult.InvalidReading)
    }

    @Test
    fun invoke_invalidReading_nothingWrittenToDb() = runTest {
        val vid = ensureVehicle()

        useCase(capture(readingKm = 0, vid = vid))

        // getLatestReading must still be null — no rows inserted
        val latest = odometerRepo.getLatestReading(vid)
        assertTrue("No row should have been inserted", latest == null)
    }

    @Test
    fun invoke_invalidReading_errorMessageIsNonEmpty() = runTest {
        val result = useCase(capture(readingKm = -1)) as OdometerSaveResult.InvalidReading
        assertTrue(result.reason.isNotBlank())
    }

    // ── Valid first reading ───────────────────────────────────────────────────────

    @Test
    fun invoke_firstValidReading_returnsSuccess() = runTest {
        val result = useCase(capture(readingKm = 1_000))

        assertTrue(result is OdometerSaveResult.Success)
    }

    @Test
    fun invoke_firstValidReading_savedCaptureHasPositiveId() = runTest {
        val result = useCase(capture(readingKm = 1_000)) as OdometerSaveResult.Success

        assertTrue("Saved id must be > 0", result.capture.id > 0)
    }

    @Test
    fun invoke_firstValidReading_savedCaptureReadingMatchesInput() = runTest {
        val result = useCase(capture(readingKm = 12_345)) as OdometerSaveResult.Success

        assertEquals(12_345, result.capture.readingKm)
    }

    // ── Regression ────────────────────────────────────────────────────────────────

    @Test
    fun invoke_regressionReading_returnsRegressionWarning() = runTest {
        val vid = ensureVehicle()
        useCase(capture(readingKm = 10_000, vid = vid)) // first

        val result = useCase(capture(readingKm = 8_000, vid = vid)) // lower → regression

        assertTrue(result is OdometerSaveResult.RegressionWarning)
    }

    @Test
    fun invoke_regressionReading_previousFieldMatchesPriorCapture() = runTest {
        val vid = ensureVehicle()
        useCase(capture(readingKm = 10_000, vid = vid))

        val result = useCase(capture(readingKm = 8_000, vid = vid)) as OdometerSaveResult.RegressionWarning

        assertEquals(10_000, result.previous)
    }

    @Test
    fun invoke_regressionReading_rowIsStillPersisted() = runTest {
        val vid = ensureVehicle()
        useCase(capture(readingKm = 10_000, vid = vid))

        val result = useCase(capture(readingKm = 8_000, vid = vid)) as OdometerSaveResult.RegressionWarning

        assertTrue("Regression row must still be saved", result.saved.id > 0)
        assertEquals(8_000, result.saved.readingKm)
    }

    // ── Progression ───────────────────────────────────────────────────────────────

    @Test
    fun invoke_progressionReading_returnsSuccess() = runTest {
        val vid = ensureVehicle()
        useCase(capture(readingKm = 5_000, vid = vid))

        val result = useCase(capture(readingKm = 6_000, vid = vid))

        assertTrue(result is OdometerSaveResult.Success)
    }

    @Test
    fun invoke_equalReading_returnsSuccess_notRegression() = runTest {
        val vid = ensureVehicle()
        useCase(capture(readingKm = 5_000, vid = vid))

        // Equal reading is not a regression (>= previous, not <)
        val result = useCase(capture(readingKm = 5_000, vid = vid))

        assertTrue(result is OdometerSaveResult.Success)
    }

    // ── Multiple captures ─────────────────────────────────────────────────────────

    @Test
    fun invoke_threeCaptures_eachGetsDistinctId() = runTest {
        val vid = ensureVehicle()

        val r1 = useCase(capture(readingKm = 1_000, vid = vid)) as OdometerSaveResult.Success
        val r2 = useCase(capture(readingKm = 2_000, vid = vid)) as OdometerSaveResult.Success
        val r3 = useCase(capture(readingKm = 3_000, vid = vid)) as OdometerSaveResult.Success

        assertNotEquals(r1.capture.id, r2.capture.id)
        assertNotEquals(r2.capture.id, r3.capture.id)
    }

    @Test
    fun invoke_vehicleIsolation_regressionCheckScopedToSameVehicle() = runTest {
        val vid1 = ensureVehicle()
        val vid2 = vehicleRepo.saveVehicle(
            VehicleEntity(ownerUid = "u1", displayName = "Car B", fiscalYear = 2026)
        )

        useCase(capture(readingKm = 50_000, vid = vid1))
        // vid2 has no prior reading, so 1_000 is a valid first capture
        val result = useCase(capture(readingKm = 1_000, vid = vid2))

        assertTrue(
            "No regression warning expected for a fresh vehicle",
            result is OdometerSaveResult.Success,
        )
    }
}
