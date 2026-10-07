package com.example.mileagetracker.data.repository

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import com.example.mileagetracker.domain.repository.RemoteSyncRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncRestoreApplierTest {

    private lateinit var database: MileageDatabase
    private lateinit var accountDataDao: AccountDataDao
    private lateinit var driveDao: DriveDao
    private lateinit var routePointDao: RoutePointDao
    private lateinit var tripAuditDao: TripAuditDao
    private lateinit var tripAttachmentDao: TripAttachmentDao
    private lateinit var vehicleDao: VehicleDao
    private lateinit var vehicleExpenseDao: VehicleExpenseDao
    private lateinit var odometerCaptureDao: OdometerCaptureDao
    private lateinit var userClassificationRuleDao: UserClassificationRuleDao
    private lateinit var evidenceSyncStore: EvidenceSyncStore
    private lateinit var applier: SyncRestoreApplier

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic("com.example.mileagetracker.data.repository.SyncAttachmentPayloadJsonKt")
        mockkStatic("com.example.mileagetracker.data.repository.SyncDrivePayloadJsonKt")
        mockkStatic("com.example.mileagetracker.data.repository.SyncExpensePayloadJsonKt")
        database = mockk()
        accountDataDao = mockk(relaxed = true)
        driveDao = mockk(relaxed = true)
        routePointDao = mockk(relaxed = true)
        tripAuditDao = mockk(relaxed = true)
        tripAttachmentDao = mockk(relaxed = true)
        vehicleDao = mockk(relaxed = true)
        vehicleExpenseDao = mockk(relaxed = true)
        odometerCaptureDao = mockk(relaxed = true)
        userClassificationRuleDao = mockk(relaxed = true)
        evidenceSyncStore = mockk(relaxed = true)

        applier = SyncRestoreApplier(
            db = database,
            accountDataDao = accountDataDao,
            driveDao = driveDao,
            routePointDao = routePointDao,
            tripAuditDao = tripAuditDao,
            tripAttachmentDao = tripAttachmentDao,
            vehicleDao = vehicleDao,
            vehicleExpenseDao = vehicleExpenseDao,
            odometerCaptureDao = odometerCaptureDao,
            userClassificationRuleDao = userClassificationRuleDao,
            evidenceSyncStore = evidenceSyncStore,
        )

        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        unmockkStatic("com.example.mileagetracker.data.repository.SyncAttachmentPayloadJsonKt")
        unmockkStatic("com.example.mileagetracker.data.repository.SyncDrivePayloadJsonKt")
        unmockkStatic("com.example.mileagetracker.data.repository.SyncExpensePayloadJsonKt")
    }

    @Test
    fun `apply drives clears owner data and inserts decoded drives`() = runTest {
        val drive = testDrive(id = 42, driverUid = "owner-1")
        val payloadJson = "drive-payload"
        every { payloadJson.toDriveEntities() } returns listOf(drive)
        val record = RemoteSyncRecord(
            entityType = SyncEntityTypes.DRIVES,
            entityId = "all",
            payloadJson = payloadJson,
            revision = 7L,
            deleted = false,
        )

        val applied = applier.apply(ownerUid = "owner-1", record = record)

        assertTrue(applied)
        coVerify { driveDao.hardDeleteDrivesForDriver("owner-1") }
        coVerify {
            driveDao.insertDrive(
                match {
                    it.id == 42 &&
                        it.driverUid == "owner-1" &&
                        it.startLocation == "Halifax" &&
                        it.endLocation == "Dartmouth"
                },
            )
        }
    }

    @Test
    fun `apply trip attachments downloads evidence and replaces stored uri`() = runTest {
        val tripId = 12
        val item = TripAttachmentSyncItem(
            entity = TripAttachmentEntity(
                id = 9,
                tripId = tripId,
                uri = "content://remote/old",
                mimeType = "image/jpeg",
                fileName = "receipt.jpg",
                createdAt = 5_000L,
            ),
            remotePath = "users/u1/evidence/trip-attachments/receipt.jpg",
        )
        val payloadJson = "attachment-payload"
        every { payloadJson.toTripAttachmentSyncItems() } returns listOf(item)
        coEvery {
            evidenceSyncStore.download(
                "users/u1/evidence/trip-attachments/receipt.jpg",
                "attachments/$tripId",
                "receipt.jpg",
            )
        } returns "file:///local/receipt.jpg"
        val record = RemoteSyncRecord(
            entityType = SyncEntityTypes.TRIP_ATTACHMENTS,
            entityId = tripId.toString(),
            payloadJson = payloadJson,
            revision = 9L,
            deleted = false,
        )

        val applied = applier.apply(ownerUid = "u1", record = record)

        assertTrue(applied)
        coVerify { tripAttachmentDao.deleteAllAttachmentsForTrip(tripId) }
        coVerify {
            evidenceSyncStore.download(
                "users/u1/evidence/trip-attachments/receipt.jpg",
                "attachments/$tripId",
                "receipt.jpg",
            )
        }
        coVerify {
            tripAttachmentDao.insertAttachment(
                match {
                    it.id == 9 &&
                        it.tripId == tripId &&
                        it.uri == "file:///local/receipt.jpg"
                },
            )
        }
    }

    @Test
    fun `apply vehicle expenses preserves existing uri when no remote path is present`() = runTest {
        val vehicleId = 4
        val expense = VehicleExpenseSyncItem(
            entity = VehicleExpenseEntity(
                id = 11,
                vehicleId = vehicleId,
                categoryKey = "fuel",
                amountCad = 87.45,
                date = 1_700_000_000_000L,
                receiptAttachmentUri = "content://receipts/original",
            ),
            remotePath = null,
        )
        val payloadJson = "expense-payload"
        every { payloadJson.toVehicleExpenseSyncItems() } returns listOf(expense)
        val record = RemoteSyncRecord(
            entityType = SyncEntityTypes.VEHICLE_EXPENSES,
            entityId = vehicleId.toString(),
            payloadJson = payloadJson,
            revision = 4L,
            deleted = false,
        )

        val applied = applier.apply(ownerUid = "owner-1", record = record)

        assertTrue(applied)
        coVerify { vehicleExpenseDao.deleteForVehicle(vehicleId) }
        coVerify(exactly = 0) { evidenceSyncStore.download(any(), any(), any()) }
        coVerify {
            vehicleExpenseDao.insert(
                match {
                    it.id == 11 &&
                        it.vehicleId == vehicleId &&
                        it.receiptAttachmentUri == "content://receipts/original"
                },
            )
        }
    }

    @Test
    fun `delete classification rules clears owner rules`() = runTest {
        val record = RemoteSyncRecord(
            entityType = SyncEntityTypes.USER_CLASSIFICATION_RULES,
            entityId = "all",
            payloadJson = "[]",
            revision = 10L,
            deleted = true,
        )

        val deleted = applier.delete(ownerUid = "owner-7", record = record)

        assertTrue(deleted)
        coVerify { accountDataDao.deleteClassificationRules("owner-7") }
    }

    @Test
    fun `apply unknown entity type returns false without running transaction`() = runTest {
        val record = RemoteSyncRecord(
            entityType = "unknown",
            entityId = "1",
            payloadJson = "{}",
            revision = 1L,
            deleted = false,
        )

        val applied = applier.apply(ownerUid = "owner-1", record = record)

        assertFalse(applied)
        coVerify(exactly = 0) { database.withTransaction(any<suspend () -> Any?>()) }
    }

    private fun testDrive(id: Int, driverUid: String): DriveEntity =
        DriveEntity(
            id = id,
            startedAt = 1_000L,
            endedAt = 2_000L,
            startLocation = "Halifax",
            endLocation = "Dartmouth",
            distanceKm = 15.2f,
            driverUid = driverUid,
            tripCategory = TripCategory.BUSINESS,
            classificationStatus = ClassificationStatus.USER_CONFIRMED,
            trackingSource = TrackingSource.AUTO_TRACKED,
            appliedRateSnapshot = 0.67f,
            createdAt = 3_000L,
            updatedAt = 4_000L,
        )
}
