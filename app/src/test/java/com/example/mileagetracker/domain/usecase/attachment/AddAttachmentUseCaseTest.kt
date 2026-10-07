package com.example.mileagetracker.domain.usecase.attachment

import android.content.Context
import android.net.Uri
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.repository.AttachmentRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AddAttachmentUseCaseTest {

    private lateinit var context: Context
    private lateinit var repository: AttachmentRepository
    private lateinit var useCase: AddAttachmentUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        repository = mockk()
        useCase = AddAttachmentUseCase(context, repository)
    }

    @Test
    fun `resolves mime and filename then delegates to repository`() = runTest {
        val uri = mockk<Uri>()
        val attachment = TripAttachment(1, 10, "file:///uri", "image/png", "test.png", 1000L)
        
        every { context.contentResolver.getType(uri) } returns "image/png"
        every { uri.lastPathSegment } returns "test.png"
        // Mock query for resolveFileName
        every { context.contentResolver.query(any(), any(), any(), any(), any()) } returns null
        
        coEvery { repository.addAttachment(10, uri, "image/png", "test.png") } returns attachment

        val result = useCase(10, uri)

        assertEquals(attachment, result)
    }
}
