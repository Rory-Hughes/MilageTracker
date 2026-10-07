package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.MissingField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetReportReadinessUseCaseTest {

    private val useCase = GetReportReadinessUseCase()

    private fun testDrive(
        id: Int = 1,
        category: TripCategory = TripCategory.UNCLASSIFIED,
        vehicleId: Int? = null,
        distance: Float = 0f,
        status: ClassificationStatus = ClassificationStatus.PENDING,
    ) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = distance,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
    )

    @Test
    fun `reportable business trip can be fully ready`() {
        val result = useCase(
            listOf(
                testDrive(
                    category = TripCategory.BUSINESS,
                    vehicleId = 1,
                    distance = 10f,
                    status = ClassificationStatus.USER_CONFIRMED,
                ),
            ),
        ).single()

        assertEquals(1.0f, result.readinessScore, 0.01f)
        assertTrue(result.missingFields.isEmpty())
        assertTrue(result.isReady)
    }

    @Test
    fun `unclassified trip is still tracked as incomplete`() {
        val result = useCase(listOf(testDrive())).single()

        assertEquals(0.0f, result.readinessScore, 0.01f)
        assertEquals(
            listOf(
                MissingField.PURPOSE,
                MissingField.VEHICLE,
                MissingField.CATEGORY,
                MissingField.DISTANCE,
            ),
            result.missingFields,
        )
    }

    @Test
    fun `personal trips are excluded from readiness results`() {
        val results = useCase(
            listOf(
                testDrive(
                    category = TripCategory.PERSONAL,
                    vehicleId = 1,
                    distance = 10f,
                    status = ClassificationStatus.USER_CONFIRMED,
                ),
            ),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `commute trips are excluded from readiness results`() {
        val results = useCase(
            listOf(
                testDrive(
                    category = TripCategory.COMMUTE,
                    vehicleId = 1,
                    distance = 10f,
                    status = ClassificationStatus.USER_CONFIRMED,
                ),
            ),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `custom trips are excluded from readiness results`() {
        val results = useCase(
            listOf(
                testDrive(
                    category = TripCategory.CUSTOM,
                    vehicleId = 1,
                    distance = 10f,
                    status = ClassificationStatus.USER_CONFIRMED,
                ).copy(purposeText = "Client follow-up"),
            ),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `reportable trips still surface missing vehicle and distance`() {
        val result = useCase(
            listOf(
                testDrive(
                    category = TripCategory.MEDICAL,
                    vehicleId = null,
                    distance = 0f,
                    status = ClassificationStatus.USER_CONFIRMED,
                ),
            ),
        ).single()

        assertEquals(0.5f, result.readinessScore, 0.01f)
        assertEquals(
            listOf(MissingField.VEHICLE, MissingField.DISTANCE),
            result.missingFields,
        )
        assertFalse(result.isReady)
    }
}
