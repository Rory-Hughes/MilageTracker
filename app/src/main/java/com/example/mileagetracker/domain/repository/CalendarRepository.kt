package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.CalendarHint

/**
 * Domain-layer contract for retrieving calendar-derived hints.
 *
 * Implementations are responsible for checking the `READ_CALENDAR`
 * permission and returning an empty list when access is unavailable.
 */
interface CalendarRepository {

    /**
     * Returns calendar events that overlap or are adjacent to the window
     * [startMs]..[endMs], mapped to [CalendarHint] domain objects.
     *
     * @param startMs  Unix epoch millis for the start of the query window.
     * @param endMs    Unix epoch millis for the end of the query window.
     * @return         List of [CalendarHint] ordered by [CalendarHint.proximityMs] ascending.
     */
    fun getEventsNear(startMs: Long, endMs: Long): List<CalendarHint>
}
