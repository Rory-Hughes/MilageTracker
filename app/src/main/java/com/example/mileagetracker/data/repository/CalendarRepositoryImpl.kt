package com.example.mileagetracker.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.mileagetracker.data.calendar.CalendarDataSource
import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [CalendarRepository] backed by [CalendarDataSource].
 *
 * Checks the `READ_CALENDAR` permission before querying; returns an empty list
 * immediately if the permission is not granted so the rest of the app degrades
 * gracefully without requiring callers to handle permission logic.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: CalendarDataSource,
) : CalendarRepository {

    override fun getEventsNear(startMs: Long, endMs: Long): List<CalendarHint> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return emptyList()

        return dataSource.queryEvents(startMs, endMs).map { event ->
            CalendarHint(
                eventTitle = event.title,
                eventLocation = event.location,
                startMs = event.startMs,
                endMs = event.endMs,
                suggestedPurpose = derivePurpose(event.title),
                proximityMs = kotlin.math.abs(event.startMs - startMs),
            )
        }.sortedBy { it.proximityMs }
    }

    /**
     * Derives a concise purpose label from a raw calendar event title.
     *
     * Rules (applied in order):
     * 1. If the title contains " — " (em-dash separator), take the text before it.
     * 2. If the title contains " - " (hyphen separator), take the text before it.
     * 3. Otherwise return the full title trimmed to 60 characters.
     *
     * Examples:
     * - "Client Meeting — Acme Corp"  → "Client Meeting"
     * - "Doctor's Appointment - Follow-up" → "Doctor's Appointment"
     * - "Team Standup"                → "Team Standup"
     */
    private fun derivePurpose(title: String): String {
        val emDash = title.indexOf(" \u2014 ")
        val hyphen = title.indexOf(" - ")
        return when {
            emDash > 0 -> title.substring(0, emDash).trim()
            hyphen > 0 -> title.substring(0, hyphen).trim()
            else -> title.trim().take(60)
        }
    }
}
