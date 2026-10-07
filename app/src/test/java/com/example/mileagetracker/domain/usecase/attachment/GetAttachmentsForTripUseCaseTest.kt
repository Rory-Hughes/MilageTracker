package com.example.mileagetracker.domain.usecase.attachment

import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetAttachmentsForTripUseCaseTest {

    private lateinit var repository: AttachmentRepository
    private lateinit var useCase: GetAttachmentsForTripUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetAttachmentsForTripUseCase(repository)
    }

    @Test
    fun `delegates to repository`() {
        val expected = flowOf(emptyList<TripAttachment>())
        every { repository.getAttachmentsForTrip(10) } returns expected
        
        val result = useCase(10)
        
        assertEquals(expected, result)
    }
}
