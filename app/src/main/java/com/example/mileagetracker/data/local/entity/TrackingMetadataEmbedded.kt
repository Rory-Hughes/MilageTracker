package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.model.TrackingMetadata

/**
 * Room-embeddable carrier for [TrackingMetadata].
 *
 * Added to [DriveEntity] via `@Embedded(prefix = "meta_")` so that all
 * columns land in the existing `drives` table without a join.
 * The `meta_` prefix avoids name collisions with top-level [DriveEntity] columns.
 *
 * All fields are nullable with defaults of `null` / `false` so that the
 * [Migration2To3] can add them with `DEFAULT NULL` / `DEFAULT 0` without
 * touching existing rows.
 */
data class TrackingMetadataEmbedded(

    /** Stored as the [DetectionSource.name] string via [RoomTypeConverters]. */
    @ColumnInfo(name = "meta_detection_source")
    val detectionSource: DetectionSource? = null,

    @ColumnInfo(name = "meta_confidence_score")
    val confidenceScore: Float? = null,

    @ColumnInfo(name = "meta_battery_level_at_start")
    val batteryLevelAtStart: Int? = null,

    @ColumnInfo(name = "meta_battery_level_at_end")
    val batteryLevelAtEnd: Int? = null,

    @ColumnInfo(name = "meta_was_battery_saver_active")
    val wasBatterySaverActive: Boolean = false,
) {
    fun toDomain(): TrackingMetadata? =
        detectionSource?.let { source ->
            confidenceScore?.let { score ->
                TrackingMetadata(
                    detectionSource = source,
                    confidenceScore = score,
                    batteryLevelAtStart = batteryLevelAtStart,
                    batteryLevelAtEnd = batteryLevelAtEnd,
                    wasBatterySaverActive = wasBatterySaverActive,
                )
            }
        }

    companion object {
        fun fromDomain(m: TrackingMetadata) = TrackingMetadataEmbedded(
            detectionSource = m.detectionSource,
            confidenceScore = m.confidenceScore,
            batteryLevelAtStart = m.batteryLevelAtStart,
            batteryLevelAtEnd = m.batteryLevelAtEnd,
            wasBatterySaverActive = m.wasBatterySaverActive,
        )
    }
}
