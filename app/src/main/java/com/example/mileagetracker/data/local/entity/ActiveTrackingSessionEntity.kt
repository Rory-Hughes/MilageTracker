package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Crash-recovery checkpoint for the one trip that can be actively recorded.
 */
@Entity(tableName = "active_tracking_sessions")
data class ActiveTrackingSessionEntity(
    @PrimaryKey
    val id: Long = ACTIVE_SESSION_ID,

    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "detection_source")
    val detectionSource: String,

    @ColumnInfo(name = "battery_level_at_start")
    val batteryLevelAtStart: Int?,

    @ColumnInfo(name = "battery_saver_seen")
    val batterySaverSeen: Boolean,

    @ColumnInfo(name = "is_manual")
    val isManual: Boolean,
) {
    companion object {
        const val ACTIVE_SESSION_ID = 1L
    }
}
