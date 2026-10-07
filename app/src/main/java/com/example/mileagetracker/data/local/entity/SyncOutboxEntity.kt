package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_outbox",
    indices = [Index("owner_uid"), Index(value = ["owner_uid", "created_at"])],
)
data class SyncOutboxEntity(
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

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "base_revision")
    val baseRevision: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
)
