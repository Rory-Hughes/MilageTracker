package com.example.mileagetracker.domain.usecase.calendar

import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.repository.CalendarRepository
import javax.inject.Inject

/**
 * Returns up to [maxHints] calendar-event-derived purpose hints for a trip
 * defined by its start and end timestamps.
 *
 * The query window is expanded by ±[windowPaddingMs] (default 30 minutes) on
 * each side to catch events that start or end just outside the trip window.
 * Results are ranked by temporal proximity to the trip start and capped at
 * [maxHints] to avoid overwhelming the UI.
 *
 * Returns an empty list when:
 * - the `READ_CALENDAR` permission is not granted,
 * - no overlapping events exist within the padded window, or
 * - [tripStartMs] is 0L (trip has no recorded start time).
 */
class GetCalendarHintsForTripUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
) {

    companion object {
        private const val WINDOW_PADDING_MS = 30L * 60 * 1_000 // 30 minutes
        private const val DEFAULT_MAX_HINTS = 3
    }

    operator fun invoke(
        tripStartMs: Long,
        tripEndMs: Long,
        maxHints: Int = DEFAULT_MAX_HINTS,
    ): List<CalendarHint> {
        if (tripStartMs == 0L) return emptyList()

        val windowStart = tripStartMs - WINDOW_PADDING_MS
        val windowEnd = (if (tripEndMs > tripStartMs) tripEndMs else tripStartMs) + WINDOW_PADDING_MS

        return calendarRepository
            .getEventsNear(windowStart, windowEnd)
            .take(maxHints)
    }
}
