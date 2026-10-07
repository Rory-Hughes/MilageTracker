package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.SyncConflictEntity

interface SyncQueueRepository {
    suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        payloadJson: String,
    )

    suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
    )

    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: SyncOperation,
        payloadJson: String,
        baseRevision: Long,
    )
}

interface SyncTransportRepository {
    suspend fun push(): SyncPushResult

    suspend fun pendingCount(): Int

    suspend fun pull(): List<RemoteSyncRecord>
}

interface SyncRestoreRepository {
    suspend fun restoreRemoteData(): SyncRestoreResult

    suspend fun hasOwnedLocalData(uid: String): Boolean
}

interface SyncConflictRepository {
    suspend fun unresolvedConflicts(): List<SyncConflictEntity>

    suspend fun resolveConflict(conflictId: String, useLocal: Boolean)
}

interface SyncCacheRepository {
    suspend fun clearLocalCache(uid: String)
}

interface SyncRepository :
    SyncQueueRepository,
    SyncTransportRepository,
    SyncRestoreRepository,
    SyncConflictRepository,
    SyncCacheRepository

enum class SyncOperation {
    UPSERT,
    DELETE,
}

data class SyncPushResult(
    val uploaded: Int,
    val conflicted: Int,
    val discardedAsStale: Int,
)

data class RemoteSyncRecord(
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val revision: Long,
    val deleted: Boolean,
)

data class SyncRestoreResult(
    val applied: Int,
    val deleted: Int,
    val skipped: Int,
)
