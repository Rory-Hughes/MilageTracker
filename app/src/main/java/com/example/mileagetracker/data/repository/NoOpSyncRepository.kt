package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.domain.repository.RemoteSyncRecord
import com.example.mileagetracker.domain.repository.SyncCacheRepository
import com.example.mileagetracker.domain.repository.SyncConflictRepository
import com.example.mileagetracker.domain.repository.SyncOperation
import com.example.mileagetracker.domain.repository.SyncPushResult
import com.example.mileagetracker.domain.repository.SyncQueueRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.SyncRestoreRepository
import com.example.mileagetracker.domain.repository.SyncRestoreResult
import com.example.mileagetracker.domain.repository.SyncTransportRepository

object NoOpSyncRepository : SyncRepository,
    SyncQueueRepository by NoOpSyncQueueRepository,
    SyncTransportRepository by NoOpSyncTransportRepository,
    SyncRestoreRepository by NoOpSyncRestoreRepository,
    SyncConflictRepository by NoOpSyncConflictRepository,
    SyncCacheRepository by NoOpSyncCacheRepository

private object NoOpSyncQueueRepository : SyncQueueRepository {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        payloadJson: String,
    ) = Unit

    override suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
    ) = Unit

    override suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: SyncOperation,
        payloadJson: String,
        baseRevision: Long,
    ) = Unit
}

private object NoOpSyncTransportRepository : SyncTransportRepository {
    override suspend fun push(): SyncPushResult = SyncPushResult(0, 0, 0)

    override suspend fun pendingCount(): Int = 0

    override suspend fun pull(): List<RemoteSyncRecord> = emptyList()
}

private object NoOpSyncRestoreRepository : SyncRestoreRepository {
    override suspend fun restoreRemoteData(): SyncRestoreResult = SyncRestoreResult(0, 0, 0)

    override suspend fun hasOwnedLocalData(uid: String): Boolean = false
}

private object NoOpSyncConflictRepository : SyncConflictRepository {
    override suspend fun unresolvedConflicts(): List<SyncConflictEntity> = emptyList()

    override suspend fun resolveConflict(conflictId: String, useLocal: Boolean) = Unit
}

private object NoOpSyncCacheRepository : SyncCacheRepository {
    override suspend fun clearLocalCache(uid: String) = Unit
}
