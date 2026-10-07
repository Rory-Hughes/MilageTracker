package com.example.mileagetracker.data.repository

import android.content.Context
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.SyncDao
import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.CloudUploadConsentRequiredException
import com.example.mileagetracker.domain.repository.RemoteSyncRecord
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.SyncCacheRepository
import com.example.mileagetracker.domain.repository.SyncConflictRepository
import com.example.mileagetracker.domain.repository.SyncOperation
import com.example.mileagetracker.domain.repository.SyncPushResult
import com.example.mileagetracker.domain.repository.SyncQueueRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.SyncRestoreRepository
import com.example.mileagetracker.domain.repository.SyncRestoreResult
import com.example.mileagetracker.domain.repository.SyncTransportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade combining 5 narrow sync repository interfaces via Kotlin interface
 * delegation. Each `by FirebaseSyncXxxRepository(...)` line binds a sub-component
 * to its interface; this class itself owns no sync logic. See the private
 * implementation classes below for the actual behavior.
 */
@Singleton
class FirebaseSyncRepository @Inject constructor(
    firestore: FirebaseFirestore,
    functions: FirebaseFunctions,
    syncDao: SyncDao,
    accountDataDao: AccountDataDao,
    currentUserProvider: CurrentUserProvider,
    settingsRepository: SettingsRepository,
    restoreApplier: SyncRestoreApplier,
    @param:ApplicationContext context: Context,
) : SyncRepository,
    SyncQueueRepository by FirebaseSyncQueueRepository(syncDao, currentUserProvider),
    SyncTransportRepository by FirebaseSyncTransportRepository(
        firestore = firestore,
        functions = functions,
        syncDao = syncDao,
        currentUserProvider = currentUserProvider,
        settingsRepository = settingsRepository,
    ),
    SyncRestoreRepository by FirebaseSyncRestoreRepository(
        firestore = firestore,
        accountDataDao = accountDataDao,
        syncDao = syncDao,
        currentUserProvider = currentUserProvider,
        restoreApplier = restoreApplier,
    ),
    SyncConflictRepository by FirebaseSyncConflictRepository(syncDao, currentUserProvider),
    SyncCacheRepository by FirebaseSyncCacheRepository(accountDataDao, syncDao, context)

private class FirebaseSyncQueueRepository(
    private val syncDao: SyncDao,
    private val currentUserProvider: CurrentUserProvider,
) : SyncQueueRepository {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        payloadJson: String,
    ) {
        val ownerUid = currentUserProvider.requireUid()
        val baseRevision = syncDao.metadata(ownerUid, entityType, entityId)?.revision ?: 0L
        enqueue(entityType, entityId, SyncOperation.UPSERT, payloadJson, baseRevision)
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String) {
        val ownerUid = currentUserProvider.requireUid()
        val baseRevision = syncDao.metadata(ownerUid, entityType, entityId)?.revision ?: 0L
        enqueue(entityType, entityId, SyncOperation.DELETE, "", baseRevision)
    }

    override suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: SyncOperation,
        payloadJson: String,
        baseRevision: Long,
    ) {
        val ownerUid = currentUserProvider.requireUid()
        val metadata = syncDao.metadata(ownerUid, entityType, entityId)
            ?: SyncMetadataEntity(
                ownerUid = ownerUid,
                entityType = entityType,
                localEntityId = entityId,
            )
        syncDao.upsertMetadata(
            metadata.copy(
                syncState = "PENDING",
                updatedAt = System.currentTimeMillis(),
                deletedAt = if (operation == SyncOperation.DELETE) {
                    System.currentTimeMillis()
                } else {
                    null
                },
            )
        )
        syncDao.enqueue(
            SyncOutboxEntity(
                syncId = metadata.syncId,
                ownerUid = ownerUid,
                entityType = entityType,
                entityId = entityId,
                operation = operation.name,
                payloadJson = payloadJson,
                baseRevision = baseRevision,
            )
        )
    }
}

private class FirebaseSyncTransportRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val syncDao: SyncDao,
    private val currentUserProvider: CurrentUserProvider,
    private val settingsRepository: SettingsRepository,
) : SyncTransportRepository {
    override suspend fun push(): SyncPushResult {
        val uid = currentUserProvider.requireUid()
        val pending = syncDao.pending(uid)
        if (pending.isEmpty()) return SyncPushResult(0, 0, 0)
        requireCloudUploadConsent()
        val results = FirebasePushBatchClient.applySyncBatch(functions, pending)
        return FirebasePushResultProcessor.process(
            syncDao = syncDao,
            uid = uid,
            pendingBySyncId = pending.associateBy { it.syncId },
            results = results,
        )
    }

    override suspend fun pendingCount(): Int =
        syncDao.pendingCount(currentUserProvider.requireUid())

    override suspend fun pull(): List<RemoteSyncRecord> {
        val uid = currentUserProvider.requireUid()
        return FirebaseSyncRecords.pull(firestore, uid)
    }

    private suspend fun requireCloudUploadConsent() {
        if (settingsRepository.getCloudUploadConsentVersion().first() !=
            CloudUploadConsent.CURRENT_VERSION
        ) {
            throw CloudUploadConsentRequiredException()
        }
    }
}

private class FirebaseSyncRestoreRepository(
    private val firestore: FirebaseFirestore,
    private val accountDataDao: AccountDataDao,
    private val syncDao: SyncDao,
    private val currentUserProvider: CurrentUserProvider,
    private val restoreApplier: SyncRestoreApplier,
) : SyncRestoreRepository {
    override suspend fun restoreRemoteData(): SyncRestoreResult {
        val uid = currentUserProvider.requireUid()
        if (hasOwnedLocalData(uid)) {
            return SyncRestoreResult(applied = 0, deleted = 0, skipped = 1)
        }
        return applyRestoreRecords(uid, pullRemoteRecords(uid))
    }

    override suspend fun hasOwnedLocalData(uid: String): Boolean =
        accountDataDao.countOwnedRootRecords(uid) > 0

    private suspend fun pullRemoteRecords(uid: String): List<RemoteSyncRecord> =
        FirebaseSyncRecords.pull(firestore, uid)
            .sortedBy { FirebaseSyncRecords.restorePriority(it.entityType) }

    private suspend fun applyRestoreRecords(
        uid: String,
        records: List<RemoteSyncRecord>,
    ): SyncRestoreResult {
        var counters = RestoreCounters()
        records.forEach { record ->
            val changed = if (record.deleted) {
                restoreApplier.delete(uid, record)
            } else {
                restoreApplier.apply(uid, record)
            }
            counters = counters + RestoreCounters.fromOutcome(record, changed)
            if (changed) upsertRestoreMetadata(uid, record)
        }
        return counters.toSyncRestoreResult()
    }

    private suspend fun upsertRestoreMetadata(uid: String, record: RemoteSyncRecord) {
        syncDao.upsertMetadata(
            SyncMetadataEntity(
                syncId = "${record.entityType}:${record.entityId}",
                ownerUid = uid,
                entityType = record.entityType,
                localEntityId = record.entityId,
                revision = record.revision,
                syncState = "SYNCED",
                updatedAt = System.currentTimeMillis(),
                deletedAt = if (record.deleted) System.currentTimeMillis() else null,
            ),
        )
    }
}

private class FirebaseSyncConflictRepository(
    private val syncDao: SyncDao,
    private val currentUserProvider: CurrentUserProvider,
) : SyncConflictRepository {
    override suspend fun unresolvedConflicts(): List<SyncConflictEntity> =
        syncDao.unresolvedConflicts(currentUserProvider.requireUid())

    override suspend fun resolveConflict(conflictId: String, useLocal: Boolean) {
        val conflict = syncDao.getConflict(conflictId) ?: return
        if (useLocal) {
            syncDao.enqueue(
                SyncOutboxEntity(
                    syncId = conflict.syncId,
                    ownerUid = conflict.ownerUid,
                    entityType = conflict.entityType,
                    entityId = conflict.entityId,
                    operation = SyncOperation.UPSERT.name,
                    payloadJson = conflict.localPayloadJson,
                    baseRevision = conflict.remoteRevision,
                )
            )
        }
        syncDao.updateConflict(conflict.copy(resolvedAt = System.currentTimeMillis()))
    }
}

private class FirebaseSyncCacheRepository(
    private val accountDataDao: AccountDataDao,
    private val syncDao: SyncDao,
    private val context: Context,
) : SyncCacheRepository {
    override suspend fun clearLocalCache(uid: String) {
        accountDataDao.clearOwnedData(uid)
        syncDao.clearOutbox(uid)
        syncDao.clearConflicts(uid)
        syncDao.clearMetadata(uid)
        File(context.filesDir, "attachments").deleteRecursively()
        File(context.filesDir, "odometer_photos").deleteRecursively()
        File(context.filesDir, "expense_receipts").deleteRecursively()
    }
}

private object FirebasePushBatchClient {
    suspend fun applySyncBatch(
        functions: FirebaseFunctions,
        pending: List<SyncOutboxEntity>,
    ): List<PushBatchResult> {
        val response = functions.getHttpsCallable("applySyncBatch")
            .call(mapOf("records" to pending.map(::toPushRecordPayload)))
            .await()
            .data as? Map<*, *> ?: error("Invalid applySyncBatch response.")
        val rawResults = response["results"] as? List<*> ?: error("Missing sync results.")
        return rawResults.mapNotNull(::parsePushBatchResult)
    }

    private fun toPushRecordPayload(item: SyncOutboxEntity): Map<String, Any?> = mapOf(
        "syncId" to item.syncId,
        "entityType" to item.entityType,
        "entityId" to item.entityId,
        "payloadJson" to item.payloadJson,
        "baseRevision" to item.baseRevision,
        "deletedAt" to item.deletedAtMillis(),
    )

    private fun parsePushBatchResult(raw: Any?): PushBatchResult? {
        val result = raw as? Map<*, *>
        val syncId = result?.get("syncId") as? String
        return when {
            result == null || syncId == null -> null
            result["status"] == "discarded" -> PushBatchResult.Discarded(syncId)
            result["status"] == "conflict" -> parseConflictResult(syncId, result)
            result["status"] == "applied" -> PushBatchResult.Applied(
                syncId = syncId,
                revision = (result["revision"] as? Number)?.toLong(),
            )
            else -> null
        }
    }

    private fun parseConflictResult(
        syncId: String,
        result: Map<*, *>,
    ): PushBatchResult.Conflict {
        val remote = result["remote"] as? Map<*, *>
        return PushBatchResult.Conflict(
            syncId = syncId,
            remotePayloadJson = remote?.get(KEY_PAYLOAD_JSON) as? String ?: "",
            remoteRevision = (remote?.get(KEY_REVISION) as? Number)?.toLong() ?: 0L,
        )
    }

    private fun SyncOutboxEntity.deletedAtMillis(): Long? {
        return if (operation == SyncOperation.DELETE.name) createdAt else null
    }
}

private object FirebasePushResultProcessor {
    suspend fun process(
        syncDao: SyncDao,
        uid: String,
        pendingBySyncId: Map<String, SyncOutboxEntity>,
        results: List<PushBatchResult>,
    ): SyncPushResult {
        var counters = PushCounters()
        results.forEach { result ->
            val outcome = applyPushBatchResult(syncDao, uid, pendingBySyncId, result) ?: return@forEach
            counters = counters + outcome
        }
        return counters.toSyncPushResult()
    }

    private suspend fun applyPushBatchResult(
        syncDao: SyncDao,
        uid: String,
        pendingBySyncId: Map<String, SyncOutboxEntity>,
        result: PushBatchResult,
    ): PushCounters? {
        val item = pendingBySyncId[result.syncId]
        val metadata = item?.let { syncDao.metadata(uid, it.entityType, it.entityId) }
        return if (item == null || metadata == null) {
            null
        } else {
            applyKnownPushBatchResult(syncDao, uid, item, metadata, result)
        }
    }

    private suspend fun applyKnownPushBatchResult(
        syncDao: SyncDao,
        uid: String,
        item: SyncOutboxEntity,
        metadata: SyncMetadataEntity,
        result: PushBatchResult,
    ): PushCounters =
        when (result) {
            is PushBatchResult.Discarded -> handleDiscardedPushItem(syncDao, item)
            is PushBatchResult.Conflict -> handleConflictedPushItem(
                syncDao = syncDao,
                uid = uid,
                item = item,
                metadata = metadata,
                result = result,
            )
            is PushBatchResult.Applied -> handleAppliedPushItem(
                syncDao = syncDao,
                item = item,
                metadata = metadata,
                revision = result.revision ?: metadata.revision,
            )
        }

    private suspend fun handleDiscardedPushItem(
        syncDao: SyncDao,
        item: SyncOutboxEntity,
    ): PushCounters {
        syncDao.removeOutboxItem(item.id)
        return PushCounters(discarded = 1)
    }

    private suspend fun handleConflictedPushItem(
        syncDao: SyncDao,
        uid: String,
        item: SyncOutboxEntity,
        metadata: SyncMetadataEntity,
        result: PushBatchResult.Conflict,
    ): PushCounters {
        syncDao.insertConflict(
            SyncConflictEntity(
                syncId = item.syncId,
                ownerUid = uid,
                entityType = item.entityType,
                entityId = item.entityId,
                localPayloadJson = item.payloadJson,
                remotePayloadJson = result.remotePayloadJson,
                localRevision = item.baseRevision,
                remoteRevision = result.remoteRevision,
            )
        )
        syncDao.upsertMetadata(metadata.copy(syncState = "CONFLICT"))
        syncDao.removeOutboxItem(item.id)
        return PushCounters(conflicted = 1)
    }

    private suspend fun handleAppliedPushItem(
        syncDao: SyncDao,
        item: SyncOutboxEntity,
        metadata: SyncMetadataEntity,
        revision: Long,
    ): PushCounters {
        syncDao.upsertMetadata(
            metadata.copy(
                revision = revision,
                syncState = "SYNCED",
                updatedAt = System.currentTimeMillis(),
            )
        )
        syncDao.removeOutboxItem(item.id)
        return PushCounters(uploaded = 1)
    }
}

private object FirebaseSyncRecords {
    suspend fun pull(
        firestore: FirebaseFirestore,
        uid: String,
    ): List<RemoteSyncRecord> =
        recordCollection(firestore, uid).get().await().documents.mapNotNull { document ->
            val entityType = document.getString(KEY_ENTITY_TYPE) ?: return@mapNotNull null
            val entityId = document.getString(KEY_ENTITY_ID) ?: return@mapNotNull null
            RemoteSyncRecord(
                entityType = entityType,
                entityId = entityId,
                payloadJson = document.getString(KEY_PAYLOAD_JSON).orEmpty(),
                revision = document.getLong(KEY_REVISION) ?: 0,
                deleted = document.getTimestamp(KEY_DELETED_AT) != null,
            )
        }

    fun restorePriority(entityType: String): Int =
        when (entityType) {
            SyncEntityTypes.VEHICLES -> 0
            SyncEntityTypes.DRIVES -> 1
            SyncEntityTypes.USER_CLASSIFICATION_RULES -> 2
            SyncEntityTypes.VEHICLE_EXPENSES -> 3
            SyncEntityTypes.ODOMETER_CAPTURES -> 4
            SyncEntityTypes.ROUTE_POINTS -> 5
            SyncEntityTypes.TRIP_AUDIT -> 6
            SyncEntityTypes.TRIP_ATTACHMENTS -> 7
            else -> 99
        }

    private fun recordCollection(
        firestore: FirebaseFirestore,
        uid: String,
    ): CollectionReference =
        firestore.collection("users").document(uid).collection("syncRecords")
}

private sealed interface PushBatchResult {
    val syncId: String

    data class Discarded(override val syncId: String) : PushBatchResult

    data class Conflict(
        override val syncId: String,
        val remotePayloadJson: String,
        val remoteRevision: Long,
    ) : PushBatchResult

    data class Applied(
        override val syncId: String,
        val revision: Long?,
    ) : PushBatchResult
}

private data class PushCounters(
    val uploaded: Int = 0,
    val conflicted: Int = 0,
    val discarded: Int = 0,
) {
    operator fun plus(other: PushCounters): PushCounters = PushCounters(
        uploaded = uploaded + other.uploaded,
        conflicted = conflicted + other.conflicted,
        discarded = discarded + other.discarded,
    )

    fun toSyncPushResult(): SyncPushResult = SyncPushResult(
        uploaded = uploaded,
        conflicted = conflicted,
        discardedAsStale = discarded,
    )
}

private data class RestoreCounters(
    val applied: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
) {
    operator fun plus(other: RestoreCounters): RestoreCounters = RestoreCounters(
        applied = applied + other.applied,
        deleted = deleted + other.deleted,
        skipped = skipped + other.skipped,
    )

    fun toSyncRestoreResult(): SyncRestoreResult = SyncRestoreResult(
        applied = applied,
        deleted = deleted,
        skipped = skipped,
    )

    companion object {
        fun fromOutcome(record: RemoteSyncRecord, changed: Boolean): RestoreCounters =
            when {
                !changed -> RestoreCounters(skipped = 1)
                record.deleted -> RestoreCounters(deleted = 1)
                else -> RestoreCounters(applied = 1)
            }
    }
}

private const val KEY_ENTITY_TYPE = "entityType"
private const val KEY_ENTITY_ID = "entityId"
private const val KEY_PAYLOAD_JSON = "payloadJson"
private const val KEY_REVISION = "revision"
private const val KEY_DELETED_AT = "deletedAt"
