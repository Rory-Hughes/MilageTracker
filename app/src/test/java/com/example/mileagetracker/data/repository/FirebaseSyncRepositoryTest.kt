package com.example.mileagetracker.data.repository

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.SyncDao
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity
import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.RemoteSyncRecord
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.CloudUploadConsentRequiredException
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.SyncOperation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirebaseSyncRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private lateinit var syncDao: SyncDao
    private lateinit var accountDataDao: AccountDataDao
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var restoreApplier: SyncRestoreApplier
    private lateinit var context: Context
    private lateinit var repository: FirebaseSyncRepository

    @Before
    fun setUp() {
        firestore = mockk()
        functions = mockk()
        syncDao = mockk()
        accountDataDao = mockk()
        currentUserProvider = mockk()
        settingsRepository = mockk()
        restoreApplier = mockk()
        context = mockk(relaxed = true)
        
        repository = FirebaseSyncRepository(
            firestore, functions, syncDao, accountDataDao,
            currentUserProvider, settingsRepository, restoreApplier, context
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `enqueue new entity creates metadata and outbox entry`() = runTest {
        val uid = "user-123"
        every { currentUserProvider.requireUid() } returns uid
        coEvery { syncDao.metadata(uid, "TRIP", "T1") } returns null
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.enqueue(any()) } returns Unit

        repository.enqueue("TRIP", "T1", SyncOperation.UPSERT, "{}", 0L)

        val metaSlot = slot<SyncMetadataEntity>()
        coVerify { syncDao.upsertMetadata(capture(metaSlot)) }
        assertEquals(uid, metaSlot.captured.ownerUid)
        assertEquals("TRIP", metaSlot.captured.entityType)
        assertEquals("T1", metaSlot.captured.localEntityId)
        assertEquals("PENDING", metaSlot.captured.syncState)
        assertNull(metaSlot.captured.deletedAt)

        val outboxSlot = slot<SyncOutboxEntity>()
        coVerify { syncDao.enqueue(capture(outboxSlot)) }
        assertEquals(uid, outboxSlot.captured.ownerUid)
        assertEquals("TRIP", outboxSlot.captured.entityType)
        assertEquals("T1", outboxSlot.captured.entityId)
        assertEquals("UPSERT", outboxSlot.captured.operation)
        assertEquals("{}", outboxSlot.captured.payloadJson)
        assertEquals(0L, outboxSlot.captured.baseRevision)
    }

    @Test
    fun `enqueue delete sets deletedAt in metadata`() = runTest {
        val uid = "user-123"
        every { currentUserProvider.requireUid() } returns uid
        coEvery { syncDao.metadata(uid, "TRIP", "T1") } returns null
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.enqueue(any()) } returns Unit

        repository.enqueue("TRIP", "T1", SyncOperation.DELETE, "", 1L)

        val metaSlot = slot<SyncMetadataEntity>()
        coVerify { syncDao.upsertMetadata(capture(metaSlot)) }
        assertNotNull(metaSlot.captured.deletedAt)
        
        coVerify { syncDao.enqueue(match { it.operation == "DELETE" }) }
    }

    @Test
    fun `enqueue upsert reuses existing syncId and clears stale deletedAt`() = runTest {
        val uid = "user-123"
        val existingMetadata = SyncMetadataEntity(
            syncId = "sync-existing",
            ownerUid = uid,
            entityType = "TRIP",
            localEntityId = "T1",
            revision = 9L,
            syncState = "SYNCED",
            updatedAt = 10L,
            deletedAt = 12345L,
        )
        every { currentUserProvider.requireUid() } returns uid
        coEvery { syncDao.metadata(uid, "TRIP", "T1") } returns existingMetadata
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.enqueue(any()) } returns Unit

        repository.enqueue("TRIP", "T1", SyncOperation.UPSERT, """{"a":1}""", 9L)

        coVerify {
            syncDao.upsertMetadata(
                match {
                    it.syncId == "sync-existing" &&
                        it.syncState == "PENDING" &&
                        it.deletedAt == null
                }
            )
        }
        coVerify {
            syncDao.enqueue(
                match {
                    it.syncId == "sync-existing" &&
                        it.ownerUid == uid &&
                        it.operation == SyncOperation.UPSERT.name &&
                        it.payloadJson == """{"a":1}""" &&
                        it.baseRevision == 9L
                }
            )
        }
    }

    @Test
    fun `enqueueUpsert uses latest metadata revision as base revision`() = runTest {
        val uid = "user-123"
        every { currentUserProvider.requireUid() } returns uid
        coEvery {
            syncDao.metadata(uid, SyncEntityTypes.DRIVES, "all")
        } returns SyncMetadataEntity(
            ownerUid = uid,
            entityType = SyncEntityTypes.DRIVES,
            localEntityId = "all",
            revision = 4L,
        )
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.enqueue(any()) } returns Unit

        repository.enqueueUpsert(SyncEntityTypes.DRIVES, "all", "[]")

        coVerify {
            syncDao.enqueue(
                match {
                    it.entityType == SyncEntityTypes.DRIVES &&
                        it.entityId == "all" &&
                        it.baseRevision == 4L &&
                        it.operation == SyncOperation.UPSERT.name
                },
            )
        }
    }

    @Test
    fun `enqueueDelete uses latest metadata revision as base revision`() = runTest {
        val uid = "user-123"
        every { currentUserProvider.requireUid() } returns uid
        coEvery {
            syncDao.metadata(uid, SyncEntityTypes.TRIP_ATTACHMENTS, "12")
        } returns SyncMetadataEntity(
            ownerUid = uid,
            entityType = SyncEntityTypes.TRIP_ATTACHMENTS,
            localEntityId = "12",
            revision = 9L,
        )
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.enqueue(any()) } returns Unit

        repository.enqueueDelete(SyncEntityTypes.TRIP_ATTACHMENTS, "12")

        coVerify {
            syncDao.enqueue(
                match {
                    it.entityType == SyncEntityTypes.TRIP_ATTACHMENTS &&
                        it.entityId == "12" &&
                        it.baseRevision == 9L &&
                        it.operation == SyncOperation.DELETE.name
                },
            )
        }
    }

    @Test
    fun `clearLocalCache deletes files and database records`() = runTest {
        coEvery { accountDataDao.clearOwnedData("u1") } returns Unit
        coEvery { syncDao.clearOutbox("u1") } returns Unit
        coEvery { syncDao.clearConflicts("u1") } returns Unit
        coEvery { syncDao.clearMetadata("u1") } returns Unit

        val filesDir = java.nio.file.Files.createTempDirectory("sync-cache-test").toFile()
        filesDir.resolve("attachments").mkdirs()
        filesDir.resolve("odometer_photos").mkdirs()
        filesDir.resolve("expense_receipts").mkdirs()
        every { context.filesDir } returns filesDir

        repository.clearLocalCache("u1")

        coVerify { accountDataDao.clearOwnedData("u1") }
        coVerify { syncDao.clearOutbox("u1") }
        coVerify { syncDao.clearConflicts("u1") }
        coVerify { syncDao.clearMetadata("u1") }
    }

    @Test
    fun `pendingCount delegates using current uid`() = runTest {
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { syncDao.pendingCount("u1") } returns 7

        val count = repository.pendingCount()

        assertEquals(7, count)
    }

    @Test
    fun `hasOwnedLocalData returns true when owned records exist`() = runTest {
        coEvery { accountDataDao.countOwnedRootRecords("u1") } returns 3

        val hasData = repository.hasOwnedLocalData("u1")

        assertEquals(true, hasData)
    }

    @Test
    fun `hasOwnedLocalData returns false when no owned records exist`() = runTest {
        coEvery { accountDataDao.countOwnedRootRecords("u1") } returns 0

        val hasData = repository.hasOwnedLocalData("u1")

        assertEquals(false, hasData)
    }

    @Test
    fun `restoreRemoteData skips when local owned data already exists`() = runTest {
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { accountDataDao.countOwnedRootRecords("u1") } returns 1

        val result = repository.restoreRemoteData()

        assertEquals(0, result.applied)
        assertEquals(0, result.deleted)
        assertEquals(1, result.skipped)
        coVerify(exactly = 0) { restoreApplier.apply(any(), any()) }
        coVerify(exactly = 0) { restoreApplier.delete(any(), any()) }
    }

    @Test
    fun `restoreRemoteData applies remote records and updates metadata`() = runTest {
        val users = mockk<CollectionReference>()
        val userDocument = mockk<DocumentReference>()
        val syncRecords = mockk<CollectionReference>()
        val task = mockk<Task<QuerySnapshot>>()
        val querySnapshot = mockk<QuerySnapshot>()
        val vehicleDocument = syncDocument(
            entityType = SyncEntityTypes.VEHICLES,
            entityId = "all",
            revision = 3L,
            deleted = false,
        )
        val attachmentDocument = syncDocument(
            entityType = SyncEntityTypes.TRIP_ATTACHMENTS,
            entityId = "12",
            revision = 4L,
            deleted = true,
        )

        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { accountDataDao.countOwnedRootRecords("u1") } returns 0
        every { firestore.collection("users") } returns users
        every { users.document("u1") } returns userDocument
        every { userDocument.collection("syncRecords") } returns syncRecords
        every { syncRecords.get() } returns task
        coEvery { task.await() } returns querySnapshot
        every { querySnapshot.documents } returns listOf(attachmentDocument, vehicleDocument)
        coEvery { restoreApplier.apply("u1", any()) } returns true
        coEvery { restoreApplier.delete("u1", any()) } returns true
        coEvery { syncDao.upsertMetadata(any()) } returns Unit

        val result = repository.restoreRemoteData()

        assertEquals(1, result.applied)
        assertEquals(1, result.deleted)
        assertEquals(0, result.skipped)
        coVerify { restoreApplier.apply("u1", match { it.entityType == SyncEntityTypes.VEHICLES }) }
        coVerify { restoreApplier.delete("u1", match { it.entityType == SyncEntityTypes.TRIP_ATTACHMENTS }) }
        coVerify(exactly = 2) { syncDao.upsertMetadata(any()) }
    }

    @Test
    fun `resolveConflict useLocal true enqueues upsert and marks conflict resolved`() = runTest {
        val conflict = SyncConflictEntity(
            id = "c1",
            syncId = "sync-1",
            ownerUid = "u1",
            entityType = "TRIP",
            entityId = "T1",
            localPayloadJson = """{"distanceKm":1.2}""",
            remotePayloadJson = """{"distanceKm":1.1}""",
            localRevision = 2L,
            remoteRevision = 5L,
            createdAt = 1_000L,
            resolvedAt = null,
        )
        coEvery { syncDao.getConflict("c1") } returns conflict
        coEvery { syncDao.enqueue(any()) } returns Unit
        coEvery { syncDao.updateConflict(any()) } returns Unit

        repository.resolveConflict("c1", useLocal = true)

        coVerify {
            syncDao.enqueue(
                match {
                    it.syncId == "sync-1" &&
                        it.ownerUid == "u1" &&
                        it.entityType == "TRIP" &&
                        it.entityId == "T1" &&
                        it.operation == SyncOperation.UPSERT.name &&
                        it.payloadJson == """{"distanceKm":1.2}""" &&
                        it.baseRevision == 5L
                }
            )
        }
        coVerify { syncDao.updateConflict(match { it.id == "c1" && it.resolvedAt != null }) }
    }

    @Test
    fun `resolveConflict useLocal false only marks conflict resolved`() = runTest {
        val conflict = SyncConflictEntity(
            id = "c2",
            syncId = "sync-2",
            ownerUid = "u1",
            entityType = "EXPENSE",
            entityId = "E1",
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            localRevision = 1L,
            remoteRevision = 2L,
            createdAt = 2_000L,
            resolvedAt = null,
        )
        coEvery { syncDao.getConflict("c2") } returns conflict
        coEvery { syncDao.updateConflict(any()) } returns Unit

        repository.resolveConflict("c2", useLocal = false)

        coVerify(exactly = 0) { syncDao.enqueue(any()) }
        coVerify { syncDao.updateConflict(match { it.id == "c2" && it.resolvedAt != null }) }
    }

    @Test
    fun `resolveConflict no-op when conflict is missing`() = runTest {
        coEvery { syncDao.getConflict("missing") } returns null

        repository.resolveConflict("missing", useLocal = true)

        coVerify(exactly = 0) { syncDao.enqueue(any()) }
        coVerify(exactly = 0) { syncDao.updateConflict(any()) }
    }

    @Test
    fun `unresolvedConflicts delegates using current uid`() = runTest {
        val conflict = SyncConflictEntity(
            id = "c3",
            syncId = "sync-3",
            ownerUid = "u1",
            entityType = "TRIP",
            entityId = "T2",
            localPayloadJson = "{}",
            remotePayloadJson = "{}",
            localRevision = 1L,
            remoteRevision = 1L,
        )
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { syncDao.unresolvedConflicts("u1") } returns listOf(conflict)

        val result = repository.unresolvedConflicts()

        assertEquals(1, result.size)
        assertEquals("c3", result.first().id)
    }

    @Test
    fun `push returns zero result when no pending records`() = runTest {
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { syncDao.pending("u1") } returns emptyList()

        val result = repository.push()

        assertEquals(0, result.uploaded)
        assertEquals(0, result.conflicted)
        assertEquals(0, result.discardedAsStale)
    }

    @Test
    fun `push applies mixed batch results and updates sync state`() = runTest {
        val callable = mockk<HttpsCallableReference>()
        val task = mockk<Task<HttpsCallableResult>>()
        val payloadSlot = slot<Map<String, List<Map<String, Any?>>>>()
        val scenario = mixedPushScenario()

        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        stubMixedPushDependencies(
            callable = callable,
            task = task,
            payloadSlot = payloadSlot,
            scenario = scenario,
        )

        val result = repository.push()

        assertMixedPushResult(result, payloadSlot)
        verifyMixedPushSideEffects(scenario)
    }

    @Test
    fun `push throws consent required when pending exists but consent is stale`() = runTest {
        val pendingItem = SyncOutboxEntity(
            syncId = "sync-1",
            ownerUid = "u1",
            entityType = "TRIP",
            entityId = "T1",
            operation = SyncOperation.UPSERT.name,
            payloadJson = "{}",
            baseRevision = 0L,
        )
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { syncDao.pending("u1") } returns listOf(pendingItem)
        every { settingsRepository.getCloudUploadConsentVersion() } returns flowOf(0)

        try {
            repository.push()
            throw AssertionError("Expected CloudUploadConsentRequiredException")
        } catch (_: CloudUploadConsentRequiredException) {
            // Expected.
        }
    }

    private fun callableResult(data: Any?): HttpsCallableResult {
        val constructor = HttpsCallableResult::class.java.getDeclaredConstructor(Any::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(data)
    }

    private fun stubMixedPushDependencies(
        callable: HttpsCallableReference,
        task: Task<HttpsCallableResult>,
        payloadSlot: io.mockk.CapturingSlot<Map<String, List<Map<String, Any?>>>>,
        scenario: MixedPushScenario,
    ) {
        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { syncDao.pending("u1") } returns scenario.pending
        every { settingsRepository.getCloudUploadConsentVersion() } returns flowOf(CloudUploadConsent.CURRENT_VERSION)
        every { functions.getHttpsCallable("applySyncBatch") } returns callable
        every { callable.call(capture(payloadSlot)) } returns task
        coEvery { task.await() } returns mixedPushCallableResult()
        coEvery { syncDao.metadata("u1", "TRIP", "T1") } returns
            syncMetadata("sync-applied", "TRIP", "T1", 2L)
        coEvery { syncDao.metadata("u1", "EXPENSE", "E1") } returns
            syncMetadata("sync-conflict", "EXPENSE", "E1", 3L)
        coEvery { syncDao.metadata("u1", "ROUTE_POINT", "R1") } returns
            syncMetadata("sync-discarded", "ROUTE_POINT", "R1", 4L)
        coEvery { syncDao.upsertMetadata(any()) } returns Unit
        coEvery { syncDao.insertConflict(any()) } returns Unit
        coEvery { syncDao.removeOutboxItem(any()) } returns Unit
    }

    private fun assertMixedPushResult(
        result: com.example.mileagetracker.domain.repository.SyncPushResult,
        payloadSlot: io.mockk.CapturingSlot<Map<String, List<Map<String, Any?>>>>,
    ) {
        assertEquals(1, result.uploaded)
        assertEquals(1, result.conflicted)
        assertEquals(1, result.discardedAsStale)
        assertEquals(3, payloadSlot.captured.getValue("records").size)
        assertEquals(300L, payloadSlot.captured.getValue("records")[2]["deletedAt"])
    }

    private fun verifyMixedPushSideEffects(scenario: MixedPushScenario) {
        coVerify {
            syncDao.upsertMetadata(match {
                it.syncId == scenario.appliedItem.syncId &&
                    it.revision == 7L &&
                    it.syncState == "SYNCED"
            })
        }
        coVerify {
            syncDao.insertConflict(match {
                it.syncId == scenario.conflictItem.syncId &&
                    it.remotePayloadJson == """{"remote":true}""" &&
                    it.remoteRevision == 9L
            })
        }
        coVerify {
            syncDao.upsertMetadata(match {
                it.syncId == scenario.conflictItem.syncId && it.syncState == "CONFLICT"
            })
        }
        coVerify { syncDao.removeOutboxItem(scenario.appliedItem.id) }
        coVerify { syncDao.removeOutboxItem(scenario.conflictItem.id) }
        coVerify { syncDao.removeOutboxItem(scenario.discardedItem.id) }
    }

    private fun mixedPushCallableResult(): HttpsCallableResult = callableResult(
        mapOf(
            "results" to listOf(
                mapOf("syncId" to "sync-applied", "status" to "applied", "revision" to 7L),
                mapOf(
                    "syncId" to "sync-conflict",
                    "status" to "conflict",
                    "remote" to mapOf("payloadJson" to """{"remote":true}""", "revision" to 9L),
                ),
                mapOf("syncId" to "sync-discarded", "status" to "discarded"),
            )
        )
    )

    private fun mixedPushScenario(): MixedPushScenario = MixedPushScenario(
        appliedItem = pushItem(
            id = "outbox-applied",
            syncId = "sync-applied",
            entityType = "TRIP",
            entityId = "T1",
            operation = SyncOperation.UPSERT.name,
            payloadJson = """{"trip":1}""",
            baseRevision = 2L,
            createdAt = 100L,
        ),
        conflictItem = pushItem(
            id = "outbox-conflict",
            syncId = "sync-conflict",
            entityType = "EXPENSE",
            entityId = "E1",
            operation = SyncOperation.UPSERT.name,
            payloadJson = """{"expense":1}""",
            baseRevision = 3L,
            createdAt = 200L,
        ),
        discardedItem = pushItem(
            id = "outbox-discarded",
            syncId = "sync-discarded",
            entityType = "ROUTE_POINT",
            entityId = "R1",
            operation = SyncOperation.DELETE.name,
            payloadJson = "",
            baseRevision = 4L,
            createdAt = 300L,
        ),
    )

    private fun pushItem(
        id: String,
        syncId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
        baseRevision: Long,
        createdAt: Long,
    ): SyncOutboxEntity = SyncOutboxEntity(
        id = id,
        syncId = syncId,
        ownerUid = "u1",
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payloadJson = payloadJson,
        baseRevision = baseRevision,
        createdAt = createdAt,
    )

    private fun syncMetadata(
        syncId: String,
        entityType: String,
        entityId: String,
        revision: Long,
    ): SyncMetadataEntity = SyncMetadataEntity(
        syncId = syncId,
        ownerUid = "u1",
        entityType = entityType,
        localEntityId = entityId,
        revision = revision,
        syncState = "PENDING",
    )

    private fun syncDocument(
        entityType: String,
        entityId: String,
        revision: Long,
        deleted: Boolean,
    ): DocumentSnapshot {
        val document = mockk<DocumentSnapshot>()
        every { document.getString("entityType") } returns entityType
        every { document.getString("entityId") } returns entityId
        every { document.getString("payloadJson") } returns "[]"
        every { document.getLong("revision") } returns revision
        every { document.getTimestamp("deletedAt") } returns if (deleted) mockk() else null
        return document
    }

    private data class MixedPushScenario(
        val appliedItem: SyncOutboxEntity,
        val conflictItem: SyncOutboxEntity,
        val discardedItem: SyncOutboxEntity,
    ) {
        val pending: List<SyncOutboxEntity>
            get() = listOf(appliedItem, conflictItem, discardedItem)
    }
}
