package com.example.mileagetracker.domain.model

/**
 * Represents a device-calendar event that temporally overlaps a trip and can
 * be offered to the user as a one-tap purpose suggestion.
 *
 * @param eventTitle    The title of the calendar event (e.g. "Client Meeting — Acme Corp").
 * @param eventLocation The optional location string stored on the calendar event.
 * @param startMs       Unix epoch millis when the event starts.
 * @param endMs         Unix epoch millis when the event ends.
 * @param suggestedPurpose  A human-readable purpose string derived from [eventTitle]
 *                          (e.g. "Client Meeting"). Shown as the tap-to-apply label.
 * @param proximityMs   Absolute difference in millis between event start and trip start;
 *                      used to rank hints — lower is better.
 */
data class CalendarHint(
    val eventTitle:       String,
    val eventLocation:    String?,
    val startMs:          Long,
    val endMs:            Long,
    val suggestedPurpose: String,
    val proximityMs:      Long,
)
