package com.example.mileagetracker.domain.usecase.calendar

import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.repository.CalendarRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetCalendarHintsForTripUseCaseTest {

    private lateinit var calendarRepository: CalendarRepository
    private lateinit var useCase: GetCalendarHintsForTripUseCase

    @Before
    fun setUp() {
        calendarRepository = mockk()
        useCase = GetCalendarHintsForTripUseCase(calendarRepository)
    }

    @Test
    fun `returns empty list when tripStartMs is 0`() {
        val result = useCase(0L, 1000L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns hints from repository within padded window`() {
        val start = 1000L
        val end = 2000L
        val padding = 30L * 60 * 1000
        val expectedStart = start - padding
        val expectedEnd = end + padding
        
        val hints = listOf(
            CalendarHint("Meeting", "Office", 1000L, 2000L, "Meeting", 0L),
            CalendarHint("Coffee", "Cafe", 2100L, 2500L, "Coffee", 100L)
        )
        
        every { calendarRepository.getEventsNear(expectedStart, expectedEnd) } returns hints
        
        val result = useCase(start, end)
        
        assertEquals(hints, result)
    }

    @Test
    fun `takes only maxHints`() {
        val hints = List(5) { 
            CalendarHint("Event $it", null, 1000L, 2000L, "Purpose $it", it.toLong()) 
        }
        every { calendarRepository.getEventsNear(any(), any()) } returns hints
        
        val result = useCase(1000L, 2000L, maxHints = 2)
        
        assertEquals(2, result.size)
        assertEquals("Event 0", result[0].eventTitle)
        assertEquals("Event 1", result[1].eventTitle)
    }
}
