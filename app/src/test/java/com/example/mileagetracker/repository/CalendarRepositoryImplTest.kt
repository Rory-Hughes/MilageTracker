package com.example.mileagetracker.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.mileagetracker.data.calendar.CalendarDataSource
import com.example.mileagetracker.data.repository.CalendarRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class CalendarRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var dataSource: CalendarDataSource
    private lateinit var repository: CalendarRepositoryImpl

    @Before
    fun setUp() {
        context = mockk()
        dataSource = mockk()
        repository = CalendarRepositoryImpl(context, dataSource)
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `getEventsNear returns empty when permission not granted`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_DENIED

        val result = repository.getEventsNear(0, 100)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getEventsNear maps and sorts events when permission granted`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        val events = listOf(
            CalendarDataSource.RawCalendarEvent("Meeting — Client", "Office", 1000L, 2000L),
            CalendarDataSource.RawCalendarEvent("Appointment - Dr", "Clinic", 5000L, 6000L),
            CalendarDataSource.RawCalendarEvent("Just a title", null, 3000L, 4000L)
        )
        every { dataSource.queryEvents(1500L, 1600L) } returns events

        val result = repository.getEventsNear(1500L, 1600L)

        assertEquals(3, result.size)
        // Check sorting (closest to 1500L first)
        // Proximity for 1000L is 500
        // Proximity for 3000L is 1500
        // Proximity for 5000L is 3500
        assertEquals("Meeting", result[0].suggestedPurpose)
        assertEquals(500L, result[0].proximityMs)
        assertEquals("Just a title", result[1].suggestedPurpose)
        assertEquals("Appointment", result[2].suggestedPurpose)
    }

    @Test
    fun `derivePurpose handles separators correctly`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        } returns PackageManager.PERMISSION_GRANTED

        val events = listOf(
            CalendarDataSource.RawCalendarEvent("Client meeting \u2014 Project X", null, 1000L, 2000L),
            CalendarDataSource.RawCalendarEvent("Dentist - Yearly", null, 1000L, 2000L)
        )
        every { dataSource.queryEvents(any(), any()) } returns events

        val result = repository.getEventsNear(1000L, 2000L)

        assertEquals("Client meeting", result[0].suggestedPurpose)
        assertEquals("Dentist", result[1].suggestedPurpose)
    }
}
