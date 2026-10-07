package com.example.mileagetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mileagetracker.domain.model.PeriodLock

@Entity(
    tableName = "period_locks",
    indices = [Index(value = ["periodYear", "periodMonth"])],
)
data class PeriodLockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodYear: Int,
    val periodMonth: Int?,
    val lockedByUserId: Long,
    val lockedAt: Long,
) {
    fun toDomain() = PeriodLock(
        id = id,
        periodYear = periodYear,
        periodMonth = periodMonth,
        lockedByUserId = lockedByUserId,
        lockedAt = lockedAt,
    )

    companion object {
        fun fromDomain(d: PeriodLock) = PeriodLockEntity(
            id = d.id,
            periodYear = d.periodYear,
            periodMonth = d.periodMonth,
            lockedByUserId = d.lockedByUserId,
            lockedAt = d.lockedAt,
        )
    }
}
