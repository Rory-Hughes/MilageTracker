package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.PeriodLock
import javax.inject.Inject

/**
 * Locks a reporting period (year + optional month) so no further edits can be made to trips or
 * expenses within that period.
 *
 * @param year            Calendar year (e.g. 2026).
 * @param month           1-based month number, or null to lock the whole year.
 * @param lockedByUserId  The manager/admin performing the lock.
 */
class LockPeriodUseCase @Inject constructor(
    private val approvalRepository: ApprovalRepository,
) {
    suspend operator fun invoke(
        year: Int,
        month: Int?,
        lockedByUserId: Long,
    ): Result<Long> {
        val alreadyLocked = approvalRepository.isLocked(year, month)
        if (alreadyLocked) {
            return Result.failure(IllegalStateException("Period $year/${month ?: "*"} is already locked."))
        }
        val lock = PeriodLock(
            periodYear = year,
            periodMonth = month,
            lockedByUserId = lockedByUserId,
            lockedAt = System.currentTimeMillis(),
        )
        return runCatching { approvalRepository.lockPeriod(lock) }
    }
}
