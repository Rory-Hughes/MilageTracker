package com.example.mileagetracker.domain.usecase.attachment

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AttachmentRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GetAttachmentsForTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: AttachmentRepositoryImpl
    private lateinit var useCase: GetAttachmentsForTripUseCase
    private var tripId: Int = 0
    private var otherTripId: Int = 0

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
        useCase = GetAttachmentsForTripUseCase(repository)

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
        otherTripId = db.driveDao().insertDrive(
            DriveEntity(
                startedAt = 3_000L,
                endedAt = 4_000L,
                startLocation = "Office",
                endLocation = "Store",
                distanceKm = 5f,
                driverUid = "u1",
                tripCategory = TripCategory.PERSONAL,
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
    fun invoke_returnsOnlyRequestedTripsAttachments_inCreatedOrder() = runTest {
        db.tripAttachmentDao().insertAttachment(
            TripAttachmentEntity(
                tripId = tripId,
                uri = "file:///first.jpg",
                mimeType = "image/jpeg",
                fileName = "first.jpg",
                createdAt = 1_000L,
            ),
        )
        db.tripAttachmentDao().insertAttachment(
            TripAttachmentEntity(
                tripId = otherTripId,
                uri = "file:///other.jpg",
                mimeType = "image/jpeg",
                fileName = "other.jpg",
                createdAt = 1_500L,
            ),
        )
        db.tripAttachmentDao().insertAttachment(
            TripAttachmentEntity(
                tripId = tripId,
                uri = "file:///second.pdf",
                mimeType = "application/pdf",
                fileName = "second.pdf",
                createdAt = 2_000L,
            ),
        )

        val attachments = useCase(tripId).first()

        assertEquals(2, attachments.size)
        assertTrue(attachments.all { it.tripId == tripId })
        assertEquals(listOf("first.jpg", "second.pdf"), attachments.map { it.fileName })
    }
}
