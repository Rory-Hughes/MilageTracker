package com.example.mileagetracker.repository

import android.content.Context
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.repository.AttachmentRepositoryImpl
import com.example.mileagetracker.domain.model.TripAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class AttachmentRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var dao: TripAttachmentDao
    private lateinit var repository: AttachmentRepositoryImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        dao = mockk()
        repository = AttachmentRepositoryImpl(context, dao)
    }

    private fun testEntity(id: Int = 0) = TripAttachmentEntity(
        id = id,
        tripId = 1,
        uri = "file:///tmp/file.png",
        mimeType = "image/png",
        fileName = "file.png",
        createdAt = 1000L
    )

    private fun testDomain(id: Int = 0) = TripAttachment(
        id = id,
        tripId = 1,
        fileUri = "file:///tmp/file.png",
        mimeType = "image/png",
        fileName = "file.png",
        capturedAt = 1000L
    )

    @Test
    fun `deleteAttachment deletes from dao`() = runTest {
        val attachment = testDomain(id = 5)
        coEvery { dao.deleteAttachment(5) } returns Unit

        repository.deleteAttachment(attachment)

        coVerify(exactly = 1) { dao.deleteAttachment(5) }
    }

    @Test
    fun `deleteAllAttachmentsForTrip deletes from dao`() = runTest {
        coEvery { dao.getAttachmentsForTripOnce(1) } returns emptyList()
        coEvery { dao.deleteAllAttachmentsForTrip(1) } returns Unit

        repository.deleteAllAttachmentsForTrip(1)

        coVerify(exactly = 1) { dao.deleteAllAttachmentsForTrip(1) }
    }

    @Test
    fun `getAttachmentsForTrip maps to domain`() = runTest {
        val entity = testEntity(id = 2)
        every { dao.getAttachmentsForTrip(1) } returns flowOf(listOf(entity))

        val result = repository.getAttachmentsForTrip(1).first()

        assertEquals(1, result.size)
        assertEquals(2, result[0].id)
    }

    @Test
    fun `getAttachmentsForTripOnce maps to domain`() = runTest {
        val entity = testEntity(id = 3)
        coEvery { dao.getAttachmentsForTripOnce(1) } returns listOf(entity)

        val result = repository.getAttachmentsForTripOnce(1)

        assertEquals(1, result.size)
        assertEquals(3, result[0].id)
    }
}
