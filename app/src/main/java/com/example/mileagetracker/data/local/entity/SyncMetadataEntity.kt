package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_metadata",
    indices = [
        Index(value = ["owner_uid", "entity_type", "local_entity_id"], unique = true),
        Index(value = ["owner_uid", "sync_state"]),
    ],
)
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "local_entity_id")
    val localEntityId: String,

    @ColumnInfo(name = "revision")
    val revision: Long = 0,

    @ColumnInfo(name = "sync_state")
    val syncState: String = "PENDING",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
