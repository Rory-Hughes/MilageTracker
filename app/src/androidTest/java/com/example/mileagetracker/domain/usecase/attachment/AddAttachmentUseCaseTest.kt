package com.example.mileagetracker.domain.usecase.attachment

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AttachmentRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI

@RunWith(AndroidJUnit4::class)
class AddAttachmentUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: AttachmentRepositoryImpl
    private lateinit var useCase: AddAttachmentUseCase
    private var tripId: Int = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = AttachmentRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            attachmentDao = db.tripAttachmentDao(),
        )
        useCase = AddAttachmentUseCase(
            context = ApplicationProvider.getApplicationContext(),
            attachmentRepository = repository,
        )

        tripId = db.driveDao().insertDrive(
            DriveEntity(
                startedAt = 1_000L,
                endedAt = 2_000L,
                startLocation = "Home",
                endLocation = "Office",
                distanceKm = 10f,
                driverUid = "u1",
                tripCategory = TripCategory.BUSINESS,
            ),
        ).toInt()
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "attachments").deleteRecursively()
        db.close()
    }

    @Test
    fun invoke_copiesFileIntoInternalStorage_andPersistsMetadata() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "attachment_${System.nanoTime()}.txt")
        source.writeText("receipt-bytes")

        val saved = useCase(
            tripId = tripId,
            sourceUri = Uri.fromFile(source),
            mimeType = "text/plain",
            fileName = "receipt.txt",
        )

        assertTrue(saved.id > 0)
        assertEquals(tripId, saved.tripId)
        assertEquals("text/plain", saved.mimeType)
        assertEquals("receipt.txt", saved.fileName)

        val copiedFile = File(URI.create(saved.fileUri))
        assertTrue(copiedFile.exists())
        assertEquals("receipt-bytes", copiedFile.readText())

        val stored = repository.getAttachmentsForTripOnce(tripId).single()
        assertEquals(saved.id, stored.id)
        assertEquals(saved.fileUri, stored.fileUri)

        source.delete()
    }
}
