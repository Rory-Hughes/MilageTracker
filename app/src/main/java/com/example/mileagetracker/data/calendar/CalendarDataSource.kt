package com.example.mileagetracker.data.calendar

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries the device [CalendarContract] for events that fall within a given
 * time window.
 *
 * Returns an empty list when:
 * - the `READ_CALENDAR` permission has not been granted, or
 * - the query returns no matching rows.
 *
 * This class intentionally performs no permission check itself; the caller
 * ([CalendarRepositoryImpl]) is responsible for verifying the permission
 * before invoking [queryEvents].  If the ContentResolver throws a
 * [SecurityException] (permission revoked mid-call) the exception is caught
 * and an empty list is returned so the rest of the UI degrades gracefully.
 */
@Singleton
class CalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class RawCalendarEvent(
        val title:    String,
        val location: String?,
        val startMs:  Long,
        val endMs:    Long,
    )

    /**
     * Returns all calendar events whose time range overlaps [windowStartMs]..[windowEndMs].
     *
     * Overlap condition: `event.dtstart < windowEnd AND event.dtend > windowStart`.
     */
    fun queryEvents(windowStartMs: Long, windowEndMs: Long): List<RawCalendarEvent> {
        return try {
            val uri = CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
            )
            val selection =
                "${CalendarContract.Events.DTSTART} < ? AND " +
                "${CalendarContract.Events.DTEND} > ? AND " +
                "${CalendarContract.Events.DELETED} = 0"
            val selectionArgs = arrayOf(
                windowEndMs.toString(),
                windowStartMs.toString(),
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC",
            ) ?: return emptyList()

            val results = mutableListOf<RawCalendarEvent>()
            cursor.use {
                val titleIdx    = it.getColumnIndex(CalendarContract.Events.TITLE)
                val locationIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx    = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx      = it.getColumnIndex(CalendarContract.Events.DTEND)
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx)?.takeIf { t -> t.isNotBlank() } ?: continue
                    results += RawCalendarEvent(
                        title    = title,
                        location = it.getString(locationIdx)?.takeIf { l -> l.isNotBlank() },
                        startMs  = it.getLong(startIdx),
                        endMs    = it.getLong(endIdx),
                    )
                }
            }
            results
        } catch (error: SecurityException) {
            Log.w(TAG, "Calendar query failed after permission check.", error)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CalendarDataSource"
    }
}
