package com.example.mileagetracker.usecase

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.MissingField
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetReportReadinessUseCaseTest {

    private lateinit var useCase: GetReportReadinessUseCase

    @Before
    fun setUp() {
        useCase = GetReportReadinessUseCase()
    }

    private fun drive(
        id: Int = 1,
        category: TripCategory = TripCategory.BUSINESS,
        vehicleId: Int? = 1,
        distanceKm: Float = 12.5f,
        status: ClassificationStatus = ClassificationStatus.USER_CONFIRMED,
    ) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = "100 King St",
        endLocation = "200 Queen St",
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
    )

    @Test
    fun `unclassified rows remain incomplete`() {
        val result = useCase(
            listOf(
                drive(
                    category = TripCategory.UNCLASSIFIED,
                    vehicleId = null,
                    distanceKm = 0f,
                    status = ClassificationStatus.PENDING,
                ),
            ),
        ).single()

        assertEquals(0.0f, result.readinessScore, 0.001f)
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
    fun `personal category is not included in report readiness`() {
        val results = useCase(listOf(drive(category = TripCategory.PERSONAL)))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `commute category is not included in report readiness`() {
        val results = useCase(listOf(drive(category = TripCategory.COMMUTE)))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `reportable category still requires vehicle and distance`() {
        val result = useCase(
            listOf(
                drive(
                    category = TripCategory.CHARITY,
                    vehicleId = null,
                    distanceKm = 0f,
                ),
            ),
        ).single()

        assertEquals(0.5f, result.readinessScore, 0.001f)
        assertEquals(
            listOf(MissingField.VEHICLE, MissingField.DISTANCE),
            result.missingFields,
        )
    }
}
