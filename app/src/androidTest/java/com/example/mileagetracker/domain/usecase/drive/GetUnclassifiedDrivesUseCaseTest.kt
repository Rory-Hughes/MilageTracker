package com.example.mileagetracker.domain.usecase.drive

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetUnclassifiedDrivesUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepository: DriveRepositoryImpl
    private lateinit var useCase: GetUnclassifiedDrivesUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        useCase = GetUnclassifiedDrivesUseCase(driveRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun drive(
        startedAt: Long,
        driverUid: String = "u1",
        tripCategory: TripCategory = TripCategory.UNCLASSIFIED,
        deletedAt: Long? = null,
    ) = DriveEntity(
        startedAt = startedAt,
        endedAt = startedAt + 60_000L,
        startLocation = "Start",
        endLocation = "End",
        distanceKm = 10f,
        driverUid = driverUid,
        tripCategory = tripCategory,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        deletedAt = deletedAt,
    )

    @Test
    fun invoke_returnsOnlyActiveUnclassifiedDrives_forRequestedDriver_newestFirst() = runTest {
        driveRepository.saveDrive(drive(startedAt = 1_000L, driverUid = "u1"))
        driveRepository.saveDrive(drive(startedAt = 3_000L, driverUid = "u1"))
        driveRepository.saveDrive(drive(startedAt = 2_000L, driverUid = "u1", tripCategory = TripCategory.BUSINESS))
        driveRepository.saveDrive(drive(startedAt = 4_000L, driverUid = "u2"))
        driveRepository.saveDrive(drive(startedAt = 5_000L, driverUid = "u1", deletedAt = 5_500L))

        val drives = useCase("u1").first()

        assertEquals(2, drives.size)
        assertTrue(drives.all { it.driverUid == "u1" })
        assertTrue(drives.all { it.tripCategory == TripCategory.UNCLASSIFIED })
        assertEquals(listOf(3_000L, 1_000L), drives.map { it.startedAt })
    }

    @Test
    fun invoke_returnsEmpty_whenDriverHasNoUnclassifiedDrives() = runTest {
        driveRepository.saveDrive(drive(startedAt = 1_000L, driverUid = "u1", tripCategory = TripCategory.BUSINESS))
        driveRepository.saveDrive(drive(startedAt = 2_000L, driverUid = "u2"))

        assertTrue(useCase("u3").first().isEmpty())
        assertTrue(useCase("u1").first().isEmpty())
    }
}
