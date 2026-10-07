package com.example.mileagetracker.domain.usecase.attachment

import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteAttachmentUseCaseTest {

    private lateinit var repository: AttachmentRepository
    private lateinit var useCase: DeleteAttachmentUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = DeleteAttachmentUseCase(repository)
    }

    @Test
    fun `delegates to repository`() = runTest {
        val attachment = TripAttachment(1, 1, "uri", "image/png", "file.png", 1000L)
        coEvery { repository.deleteAttachment(attachment) } returns Unit
        
        useCase(attachment)
        
        coVerify { repository.deleteAttachment(attachment) }
    }
}
