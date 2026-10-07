package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.data.local.entity.AuditEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [AccountDataDao] against a real (in-memory) SQLite
 * database. These exercise the multi-table counting and claiming queries used during
 * account migration, and the [clearOwnedData] @Transaction that bulk-deletes all
 * data owned by a given uid while leaving other users' data intact.
 *
 * ## Setup
 * Most tests insert rows into multiple tables to verify cross-table SQL:
 * - `drives` and `vehicles` as root tables.
 * - `route_points`, `trip_audit`, `trip_attachments` referencing drives.
 * - `vehicle_expenses` and `odometer_captures` referencing vehicles.
 * - `active_tracking_sessions` for the ownerUid.
 */
@RunWith(AndroidJUnit4::class)
class AccountDataDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: AccountDataDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.accountDataDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private suspend fun insertDrive(driverUid: String, id: Int = 0): Int {
        return db.driveDao().insertDrive(
            DriveEntity(
                id = id,
                startedAt = 1_000L,
                startLocation = "A",
                endLocation = "B",
                distanceKm = 10f,
                driverUid = driverUid,
                tripCategory = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource = TrackingSource.AUTO_TRACKED,
            ),
        ).toInt()
    }

    private suspend fun insertVehicle(ownerUid: String): Int {
        return db.vehicleDao().insertVehicle(
            VehicleEntity(
                ownerUid = ownerUid,
                displayName = "Car",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()
    }

    private suspend fun insertRoutePoint(tripId: Int) {
        db.routePointDao().insertAll(
            listOf(
                RoutePointEntity(
                    tripId = tripId.toLong(),
                    lat = 45.0,
                    lng = -66.0,
                    altitudeMetres = null,
                    accuracyMetres = null,
                    speedKph = null,
                    timestampMs = 1_000L,
                ),
            ),
        )
    }

    private suspend fun insertAuditRow(tripId: Int) {
        db.tripAuditDao().insertAuditRow(
            TripAuditEntity(
                tripId = tripId,
                actorUid = "u1",
                eventType = AuditEventType.DRIVE_CREATED,
                changedAt = 1_000L,
            ),
        )
    }

    private suspend fun insertAttachment(tripId: Int) {
        db.tripAttachmentDao().insertAttachment(
            TripAttachmentEntity(
                tripId = tripId,
                uri = "content://receipts/$tripId",
                mimeType = "image/jpeg",
                fileName = "receipt.jpg",
            ),
        )
    }

    private suspend fun insertExpense(vehicleId: Int) {
        db.vehicleExpenseDao().insert(
            VehicleExpenseEntity(
                vehicleId = vehicleId,
                categoryKey = "fuel",
                amountCad = 50.0,
                date = 1_000L,
            ),
        )
    }

    private suspend fun insertOdometerCapture(vehicleId: Int) {
        db.odometerCaptureDao().insert(
            OdometerCaptureEntity(
                vehicleId = vehicleId,
                captureDate = 1_000L,
                readingKm = 10_000,
            ),
        )
    }

    private suspend fun insertActiveSession(ownerUid: String) {
        db.activeTrackingDao().upsertSession(
            ActiveTrackingSessionEntity(
                id = ActiveTrackingSessionEntity.ACTIVE_SESSION_ID,
                ownerUid = ownerUid,
                startedAt = 1_000L,
                detectionSource = "AUTO",
                batteryLevelAtStart = null,
                batterySaverSeen = false,
                isManual = false,
            ),
        )
    }

    private suspend fun insertClassificationRule(
        ownerUid: String,
        name: String = "Rule",
    ): Int {
        return db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid = ownerUid,
                name = name,
                keyword = name.lowercase(),
                category = TripCategory.BUSINESS.name,
                confidence = 0.8f,
                priority = 10,
                isEnabled = true,
                createdAt = 1_000L,
            ),
        ).toInt()
    }

    // ── countOwnedRootRecords ──────────────────────────────────────────────────

    @Test
    fun countOwnedRootRecords_sumsDrivesAndVehiclesForUid() = runTest {
        insertDrive("u1")
        insertDrive("u1")
        insertVehicle("u1")
        insertClassificationRule("u1")
        insertDrive("u2") // other user, excluded
        insertVehicle("u2")
        insertClassificationRule("u2")

        // u1 has 2 drives + 1 vehicle + 1 rule = 4
        assertEquals(4, dao.countOwnedRootRecords("u1"))
        assertEquals(3, dao.countOwnedRootRecords("u2"))
        assertEquals(0, dao.countOwnedRootRecords("u3"))
    }

    // ── countLegacyDrives / claimLegacyDrives ─────────────────────────────────

    @Test
    fun countLegacyDrives_countsNullAndLegacyUidDrives() = runTest {
        insertDrive(driverUid = "real-user")
        insertDrive(driverUid = "local")
        insertDrive(driverUid = "SYSTEM")
        insertDrive(driverUid = "LEGACY_USER")

        assertEquals(3, dao.countLegacyDrives())
    }

    @Test
    fun claimLegacyDrives_updatesLegacyUidToNewUid_returnsCount() = runTest {
        insertDrive(driverUid = "local")
        insertDrive(driverUid = "SYSTEM")
        insertDrive(driverUid = "real-user") // must not be claimed

        val claimed = dao.claimLegacyDrives("new-user")

        assertEquals(2, claimed)
        assertEquals(0, dao.countLegacyDrives())
        // The real-user drive should still exist under its original uid.
        assertEquals(1, dao.countOwnedRootRecords("real-user"))
    }

    // ── countLegacyVehicles / claimLegacyVehicles ─────────────────────────────

    @Test
    fun countLegacyVehicles_countsLegacyOwnerUidVehicles() = runTest {
        insertVehicle("real-user")
        insertVehicle("local")
        insertVehicle("SYSTEM")

        assertEquals(2, dao.countLegacyVehicles())
    }

    @Test
    fun claimLegacyVehicles_updatesOwnerUid_leavesRealUsersVehicle() = runTest {
        insertVehicle("LEGACY_USER")
        insertVehicle("real-user")

        val claimed = dao.claimLegacyVehicles("new-user")

        assertEquals(1, claimed)
        assertEquals(0, dao.countLegacyVehicles())
    }

    @Test
    fun countLegacyClassificationRules_countsLegacyOwners() = runTest {
        insertClassificationRule("real-user", name = "Real")
        insertClassificationRule("LEGACY_USER", name = "Legacy")

        assertEquals(1, dao.countLegacyClassificationRules())
    }

    @Test
    fun claimLegacyClassificationRules_updatesOwnerUid() = runTest {
        insertClassificationRule("LEGACY_USER", name = "Legacy")
        insertClassificationRule("real-user", name = "Real")

        val claimed = dao.claimLegacyClassificationRules("new-user")

        assertEquals(1, claimed)
        assertEquals(0, dao.countLegacyClassificationRules())
        assertEquals(1, db.userClassificationRuleDao().observeAll("new-user").first().size)
        assertEquals(1, db.userClassificationRuleDao().observeAll("real-user").first().size)
    }

    // ── clearOwnedData @Transaction ───────────────────────────────────────────

    @Test
    fun clearOwnedData_deletesAllUserDataAcrossTables_keepsOtherUsersData() = runTest {
        // Seed u1's data across all tables
        val driveId = insertDrive("u1", id = 100)
        val vehicleId = insertVehicle("u1")
        insertRoutePoint(driveId)
        insertAuditRow(driveId)
        insertAttachment(driveId)
        insertExpense(vehicleId)
        insertOdometerCapture(vehicleId)
        insertActiveSession("u1")
        insertClassificationRule("u1", name = "u1 rule")

        // Seed u2's data (must survive)
        val drive2Id = insertDrive("u2", id = 200)
        val vehicle2Id = insertVehicle("u2")
        insertRoutePoint(drive2Id)
        insertExpense(vehicle2Id)
        insertClassificationRule("u2", name = "u2 rule")

        dao.clearOwnedData("u1")

        // u1's root rows gone
        assertEquals(0, dao.countOwnedRootRecords("u1"))

        // u1's child rows gone
        assertTrue(db.routePointDao().getPointsForTripOnce(driveId.toLong()).isEmpty())
        assertTrue(db.tripAuditDao().getAuditRowsForTripOnce(driveId).isEmpty())
        assertTrue(db.tripAttachmentDao().getAttachmentsForTripOnce(driveId).isEmpty())
        assertTrue(db.vehicleExpenseDao().getForVehicle(vehicleId).first().isEmpty())
        assertTrue(db.odometerCaptureDao().getForVehicle(vehicleId).first().isEmpty())
        assertEquals(null, db.activeTrackingDao().getSession())
        assertTrue(db.userClassificationRuleDao().observeAll("u1").first().isEmpty())

        // u2's data intact
        assertEquals(3, dao.countOwnedRootRecords("u2"))
        assertEquals(1, db.routePointDao().getPointsForTripOnce(drive2Id.toLong()).size)
        assertEquals(1, db.vehicleExpenseDao().getForVehicle(vehicle2Id).first().size)
        assertEquals(listOf("u2 rule"), db.userClassificationRuleDao().observeAll("u2").first().map { it.name })
    }

    @Test
    fun clearOwnedData_whenUserHasNoData_isNoOp() = runTest {
        insertDrive("u2")

        dao.clearOwnedData("u1") // u1 has no data

        assertEquals(1, dao.countOwnedRootRecords("u2"))
    }
}
