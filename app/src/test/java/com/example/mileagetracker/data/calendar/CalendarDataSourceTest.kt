package com.example.mileagetracker.data.calendar

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.CalendarContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarDataSourceTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var dataSource: CalendarDataSource

    @Before
    fun setUp() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        dataSource = CalendarDataSource(context)
    }

    @Test
    fun `queryEvents returns empty list when resolver returns null cursor`() {
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returns null

        val result = dataSource.queryEvents(100L, 200L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryEvents returns empty list when resolver throws security exception`() {
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } throws SecurityException("permission revoked")

        val result = dataSource.queryEvents(100L, 200L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queryEvents maps valid rows and skips blank titles`() {
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returns matrixCursor(
            row("Client Meeting", "Downtown Office", 1_000L, 2_000L),
            row("   ", "Ignored", 3_000L, 4_000L),
            row("Dentist", null, 5_000L, 6_000L),
        )

        val result = dataSource.queryEvents(500L, 10_000L)

        assertEquals(2, result.size)
        assertEquals("Client Meeting", result[0].title)
        assertEquals("Downtown Office", result[0].location)
        assertEquals(1_000L, result[0].startMs)
        assertEquals(2_000L, result[0].endMs)
        assertEquals("Dentist", result[1].title)
        assertNull(result[1].location)
    }

    @Test
    fun `queryEvents converts blank location to null`() {
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returns matrixCursor(
            row("Trip", "   ", 10L, 20L),
        )

        val result = dataSource.queryEvents(0L, 100L)

        assertEquals(1, result.size)
        assertNull(result.single().location)
    }

    @Test
    fun `queryEvents sends overlap selection window and ascending sort order`() {
        val projectionSlot = slot<Array<String>>()
        val selectionSlot = slot<String>()
        val argsSlot = slot<Array<String>>()
        val sortOrderSlot = slot<String>()

        every {
            contentResolver.query(
                any(),
                capture(projectionSlot),
                capture(selectionSlot),
                capture(argsSlot),
                capture(sortOrderSlot),
            )
        } returns matrixCursor()

        dataSource.queryEvents(windowStartMs = 111L, windowEndMs = 999L)

        assertEquals(
            arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
            ).toList(),
            projectionSlot.captured.toList(),
        )
        assertTrue(selectionSlot.captured.contains("${CalendarContract.Events.DTSTART} < ?"))
        assertTrue(selectionSlot.captured.contains("${CalendarContract.Events.DTEND} > ?"))
        assertTrue(selectionSlot.captured.contains("${CalendarContract.Events.DELETED} = 0"))
        assertEquals(listOf("999", "111"), argsSlot.captured.toList())
        assertEquals("${CalendarContract.Events.DTSTART} ASC", sortOrderSlot.captured)
    }

    @Test
    fun `queryEvents returns empty when all rows have blank titles`() {
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returns matrixCursor(
            row("", "A", 1L, 2L),
            row("   ", "B", 3L, 4L),
        )

        val result = dataSource.queryEvents(0L, 10L)

        assertTrue(result.isEmpty())
    }

    private fun matrixCursor(vararg rows: Array<Any?>): MatrixCursor {
        val cursor = MatrixCursor(
            arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
            )
        )
        rows.forEach { cursor.addRow(it) }
        return cursor
    }

    private fun row(
        title: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
    ): Array<Any?> = arrayOf(title, location, startMs, endMs)
}
