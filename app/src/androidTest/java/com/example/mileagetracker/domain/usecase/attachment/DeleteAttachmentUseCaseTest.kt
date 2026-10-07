package com.example.mileagetracker.domain.usecase.attachment

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AttachmentRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI

@RunWith(AndroidJUnit4::class)
class DeleteAttachmentUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: AttachmentRepositoryImpl
    private lateinit var addUseCase: AddAttachmentUseCase
    private lateinit var deleteUseCase: DeleteAttachmentUseCase
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
        addUseCase = AddAttachmentUseCase(
            context = ApplicationProvider.getApplicationContext(),
            attachmentRepository = repository,
        )
        deleteUseCase = DeleteAttachmentUseCase(repository)

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
    fun invoke_removesAttachmentRow_andCopiedFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "attachment_${System.nanoTime()}.jpg")
        source.writeText("image-bytes")

        val attachment = addUseCase(
            tripId = tripId,
            sourceUri = Uri.fromFile(source),
            mimeType = "image/jpeg",
            fileName = "receipt.jpg",
        )
        val copiedFile = File(URI.create(attachment.fileUri))
        assertTrue(copiedFile.exists())

        deleteUseCase(attachment)

        assertTrue(repository.getAttachmentsForTrip(tripId).first().isEmpty())
        assertFalse(copiedFile.exists())

        source.delete()
    }
}
