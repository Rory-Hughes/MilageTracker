package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.SyncConflictEntity
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity

interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE owner_uid = :ownerUid ORDER BY created_at ASC")
    suspend fun pending(ownerUid: String): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun removeOutboxItem(id: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE owner_uid = :ownerUid")
    suspend fun pendingCount(ownerUid: String): Int

    @Query("DELETE FROM sync_outbox WHERE owner_uid = :ownerUid")
    suspend fun clearOutbox(ownerUid: String)
}

interface SyncMetadataDao {
    @Query(
        "SELECT * FROM sync_metadata WHERE owner_uid = :ownerUid " +
            "AND entity_type = :entityType " +
            "AND local_entity_id = :entityId LIMIT 1"
    )
    suspend fun metadata(ownerUid: String, entityType: String, entityId: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE owner_uid = :ownerUid")
    suspend fun clearMetadata(ownerUid: String)
}

interface SyncConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query(
        "SELECT * FROM sync_conflicts WHERE owner_uid = :ownerUid " +
            "AND resolved_at IS NULL ORDER BY created_at ASC"
    )
    suspend fun unresolvedConflicts(ownerUid: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getConflict(id: String): SyncConflictEntity?

    @Update
    suspend fun updateConflict(conflict: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE owner_uid = :ownerUid")
    suspend fun clearConflicts(ownerUid: String)
}

@Dao
interface SyncDao :
    SyncOutboxDao,
    SyncMetadataDao,
    SyncConflictDao
