package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_conflicts",
    indices = [Index("owner_uid"), Index(value = ["owner_uid", "resolved_at"])],
)
data class SyncConflictEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "sync_id")
    val syncId: String,

    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "local_payload_json")
    val localPayloadJson: String,

    @ColumnInfo(name = "remote_payload_json")
    val remotePayloadJson: String,

    @ColumnInfo(name = "local_revision")
    val localRevision: Long,

    @ColumnInfo(name = "remote_revision")
    val remoteRevision: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
)
