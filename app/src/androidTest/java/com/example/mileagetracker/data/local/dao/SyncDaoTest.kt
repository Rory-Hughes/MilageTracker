package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [SyncDao] against a real (in-memory) SQLite database.
 * Covers all three sync tables — outbox, metadata, conflicts — including owner-uid
 * scoping, FIFO ordering for pending items, unresolved-only conflict filtering,
 * REPLACE upsert semantics, the clear* bulk-delete operations, and metadata lookup
 * by compound key (ownerUid + entityType + entityId).
 */
@RunWith(AndroidJUnit4::class)
class SyncDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: SyncDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.syncDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun outboxItem(
        id: String,
        ownerUid: String = "u1",
        entityType: String = "DRIVE",
        entityId: String = "e1",
        operation: String = "CREATE",
        payloadJson: String = "{}",
        baseRevision: Long = 0L,
        createdAt: Long = 1_000L,
        attemptCount: Int = 0,
    ) = SyncOutboxEntity(
        id = id,
        syncId = "sync-$id",
        ownerUid = ownerUid,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payloadJson = payloadJson,
        baseRevision = baseRevision,
        createdAt = createdAt,
        attemptCount = attemptCount,
    )

    private fun metadata(
        syncId: String = "meta-1",
        ownerUid: String = "u1",
        entityType: String = "DRIVE",
        localEntityId: String = "e1",
        revision: Long = 0L,
        syncState: String = "PENDING",
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
    ) = SyncMetadataEntity(
        syncId = syncId,
        ownerUid = ownerUid,
        entityType = entityType,
        localEntityId = localEntityId,
        revision = revision,
        syncState = syncState,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun conflict(
        id: String,
        ownerUid: String = "u1",
        entityType: String = "DRIVE",
        entityId: String = "e1",
        localPayloadJson: String = """{"local":true}""",
        remotePayloadJson: String = """{"remote":true}""",
        localRevision: Long = 1L,
        remoteRevision: Long = 2L,
        createdAt: Long = 1_000L,
        resolvedAt: Long? = null,
    ) = SyncConflictEntity(
        id = id,
        syncId = "sync-$id",
        ownerUid = ownerUid,
        entityType = entityType,
        entityId = entityId,
        localPayloadJson = localPayloadJson,
        remotePayloadJson = remotePayloadJson,
        localRevision = localRevision,
        remoteRevision = remoteRevision,
        createdAt = createdAt,
        resolvedAt = resolvedAt,
    )

    // ── outbox: enqueue + pending ──────────────────────────────────────────────

    @Test
    fun enqueue_thenPending_roundTripsAndOrdersByCreatedAtAsc() = runTest {
        dao.enqueue(outboxItem(id = "c", ownerUid = "u1", createdAt = 3_000L))
        dao.enqueue(outboxItem(id = "a", ownerUid = "u1", createdAt = 1_000L))
        dao.enqueue(outboxItem(id = "b", ownerUid = "u1", createdAt = 2_000L))

        val items = dao.pending("u1")
        assertEquals(listOf("a", "b", "c"), items.map { it.id })
    }

    @Test
    fun pending_scopesToOwnerUid() = runTest {
        dao.enqueue(outboxItem(id = "u1-item", ownerUid = "u1"))
        dao.enqueue(outboxItem(id = "u2-item", ownerUid = "u2"))

        assertEquals(1, dao.pending("u1").size)
        assertEquals("u1-item", dao.pending("u1").single().id)
    }

    @Test
    fun pending_returnsEmpty_whenNoItemsForOwner() = runTest {
        assertTrue(dao.pending("u1").isEmpty())
    }

    // ── outbox: removeOutboxItem + pendingCount ────────────────────────────────

    @Test
    fun removeOutboxItem_removesOnlyThatItem() = runTest {
        dao.enqueue(outboxItem(id = "keep"))
        dao.enqueue(outboxItem(id = "delete"))

        dao.removeOutboxItem("delete")

        assertEquals(1, dao.pending("u1").size)
        assertEquals("keep", dao.pending("u1").single().id)
    }

    @Test
    fun pendingCount_scopesToOwnerUid() = runTest {
        dao.enqueue(outboxItem(id = "u1-a", ownerUid = "u1"))
        dao.enqueue(outboxItem(id = "u1-b", ownerUid = "u1"))
        dao.enqueue(outboxItem(id = "u2-a", ownerUid = "u2"))

        assertEquals(2, dao.pendingCount("u1"))
        assertEquals(1, dao.pendingCount("u2"))
        assertEquals(0, dao.pendingCount("u3"))
    }

    // ── outbox: REPLACE conflict strategy ─────────────────────────────────────

    @Test
    fun enqueue_withSameId_replacesItem() = runTest {
        dao.enqueue(outboxItem(id = "x", operation = "CREATE"))
        dao.enqueue(outboxItem(id = "x", operation = "UPDATE"))

        assertEquals(1, dao.pending("u1").size)
        assertEquals("UPDATE", dao.pending("u1").single().operation)
    }

    // ── outbox: clearOutbox ────────────────────────────────────────────────────

    @Test
    fun clearOutbox_removesAllItemsForOwner_keepsOtherOwner() = runTest {
        dao.enqueue(outboxItem(id = "u1-a", ownerUid = "u1"))
        dao.enqueue(outboxItem(id = "u1-b", ownerUid = "u1"))
        dao.enqueue(outboxItem(id = "u2-a", ownerUid = "u2"))

        dao.clearOutbox("u1")

        assertTrue(dao.pending("u1").isEmpty())
        assertEquals(1, dao.pending("u2").size)
    }

    // ── metadata: upsertMetadata + metadata lookup ─────────────────────────────

    @Test
    fun upsertMetadata_thenLookup_byCompoundKey() = runTest {
        dao.upsertMetadata(
            metadata(
                syncId = "m1",
                ownerUid = "u1",
                entityType = "DRIVE",
                localEntityId = "drive-99",
                revision = 5L,
                syncState = "SYNCED",
            ),
        )

        val loaded = dao.metadata("u1", "DRIVE", "drive-99")!!
        assertEquals("m1", loaded.syncId)
        assertEquals(5L, loaded.revision)
        assertEquals("SYNCED", loaded.syncState)
    }

    @Test
    fun metadata_returnsNull_forUnknownCompoundKey() = runTest {
        assertNull(dao.metadata("u1", "DRIVE", "no-such-id"))
    }

    @Test
    fun upsertMetadata_withSameSyncId_replacesRow() = runTest {
        dao.upsertMetadata(metadata(syncId = "m1", syncState = "PENDING"))
        dao.upsertMetadata(metadata(syncId = "m1", syncState = "SYNCED"))

        assertEquals("SYNCED", dao.metadata("u1", "DRIVE", "e1")!!.syncState)
    }

    // ── metadata: clearMetadata ────────────────────────────────────────────────

    @Test
    fun clearMetadata_removesOwnerRowsOnly() = runTest {
        dao.upsertMetadata(metadata(syncId = "u1-meta", ownerUid = "u1"))
        dao.upsertMetadata(metadata(syncId = "u2-meta", ownerUid = "u2", localEntityId = "e2"))

        dao.clearMetadata("u1")

        assertNull(dao.metadata("u1", "DRIVE", "e1"))
        assertNotNull(dao.metadata("u2", "DRIVE", "e2"))
    }

    // ── conflicts: insertConflict + unresolvedConflicts ────────────────────────

    @Test
    fun insertConflict_thenUnresolved_returnsOnlyNullResolvedAt_oldestFirst() = runTest {
        dao.insertConflict(conflict(id = "c1", ownerUid = "u1", createdAt = 2_000L))
        dao.insertConflict(conflict(id = "c2", ownerUid = "u1", createdAt = 1_000L))
        dao.insertConflict(conflict(id = "c3", ownerUid = "u1", createdAt = 3_000L, resolvedAt = 4_000L)) // resolved

        val unresolved = dao.unresolvedConflicts("u1")

        assertEquals(2, unresolved.size)
        assertEquals(listOf("c2", "c1"), unresolved.map { it.id }) // ASC by created_at
        assertTrue(unresolved.all { it.resolvedAt == null })
    }

    @Test
    fun unresolvedConflicts_scopesToOwner() = runTest {
        dao.insertConflict(conflict(id = "u1-c", ownerUid = "u1"))
        dao.insertConflict(conflict(id = "u2-c", ownerUid = "u2"))

        assertEquals(1, dao.unresolvedConflicts("u1").size)
        assertEquals("u1-c", dao.unresolvedConflicts("u1").single().id)
    }

    // ── conflicts: getConflict + updateConflict ────────────────────────────────

    @Test
    fun getConflict_returnsRowById_orNull() = runTest {
        dao.insertConflict(conflict(id = "cf1"))

        assertNotNull(dao.getConflict("cf1"))
        assertNull(dao.getConflict("no-such"))
    }

    @Test
    fun updateConflict_persistsResolvedAt() = runTest {
        dao.insertConflict(conflict(id = "cf1", resolvedAt = null))
        val existing = dao.getConflict("cf1")!!

        dao.updateConflict(existing.copy(resolvedAt = 9_000L))

        assertEquals(9_000L, dao.getConflict("cf1")!!.resolvedAt)
        assertTrue(dao.unresolvedConflicts("u1").isEmpty())
    }

    // ── conflicts: clearConflicts ─────────────────────────────────────────────

    @Test
    fun clearConflicts_removesOwnerConflictsOnly() = runTest {
        dao.insertConflict(conflict(id = "u1-c", ownerUid = "u1"))
        dao.insertConflict(conflict(id = "u2-c", ownerUid = "u2"))

        dao.clearConflicts("u1")

        assertTrue(dao.unresolvedConflicts("u1").isEmpty())
        assertEquals(1, dao.unresolvedConflicts("u2").size)
    }
}
