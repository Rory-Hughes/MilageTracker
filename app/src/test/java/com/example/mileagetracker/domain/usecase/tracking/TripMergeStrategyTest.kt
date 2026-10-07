package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.repository.AuditRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TripMergeStrategyTest {

    private fun testDrive(
        id: Int,
        start: Long,
        end: Long,
        dist: Float = 10f,
        status: ClassificationStatus = ClassificationStatus.PENDING,
        notes: String? = null
    ) = DriveEntity(
        id = id,
        startedAt = start,
        endedAt = end,
        startLocation = "Loc_$start",
        endLocation = "Loc_$end",
        distanceKm = dist,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = start,
        notes = notes
    )

    @Test
    fun `merge keeps earliest start and latest end`() {
        val existing = testDrive(id = 1, start = 1000L, end = 2000L)
        val candidate = testDrive(id = 0, start = 900L, end = 2100L)

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(1, merged.id)
        assertEquals(900L, merged.startedAt)
        assertEquals(2100L, merged.endedAt)
        assertEquals("Loc_900", merged.startLocation)
        assertEquals("Loc_2100", merged.endLocation)
    }

    @Test
    fun `merge keeps maximum distance`() {
        val existing = testDrive(id = 1, start = 1000L, end = 2000L, dist = 5.0f)
        val candidate = testDrive(id = 0, start = 1000L, end = 2000L, dist = 7.5f)

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(7.5f, merged.distanceKm)
    }

    @Test
    fun `merge concatenates notes`() {
        val existing = testDrive(id = 1, start = 1000L, end = 2000L, notes = "Notes A")
        val candidate = testDrive(id = 0, start = 1000L, end = 2000L, notes = "Notes B")

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals("Notes A | Notes B", merged.notes)
    }

    @Test
    fun `merge prefers existing classification if not pending`() {
        val existing = testDrive(
            id = 1,
            start = 1000L,
            end = 2000L,
            status = ClassificationStatus.USER_CONFIRMED,
        ).copy(tripCategory = TripCategory.BUSINESS)
        val candidate = testDrive(id = 0, start = 1000L, end = 2000L).copy(tripCategory = TripCategory.PERSONAL)

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(TripCategory.BUSINESS, merged.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, merged.classificationStatus)
    }

    @Test
    fun `merge uses candidate classification when existing is needs review`() {
        val existing = testDrive(
            id = 1,
            start = 1000L,
            end = 2000L,
            status = ClassificationStatus.NEEDS_REVIEW,
        ).copy(tripCategory = TripCategory.UNCLASSIFIED)
        val candidate = testDrive(
            id = 0,
            start = 1100L,
            end = 2100L,
            status = ClassificationStatus.USER_CONFIRMED,
        ).copy(tripCategory = TripCategory.BUSINESS)

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(TripCategory.BUSINESS, merged.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, merged.classificationStatus)
    }

    @Test
    fun `merge falls back endedAt from startedAt when source endedAt is null`() {
        val existing = testDrive(id = 1, start = 1_000L, end = 1_500L)
        val candidate = testDrive(id = 0, start = 2_000L, end = 2_500L).copy(endedAt = null)

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(2_000L, merged.endedAt)
    }

    @Test
    fun `merge keeps existing applied rate when non-zero and falls back vehicle and purpose`() {
        val existing = testDrive(id = 1, start = 1000L, end = 2000L).copy(
            appliedRateSnapshot = 0.73f,
            vehicleId = null,
            purposeText = null,
        )
        val candidate = testDrive(id = 0, start = 900L, end = 2100L).copy(
            appliedRateSnapshot = 0.67f,
            vehicleId = 8,
            purposeText = "Client visit",
        )

        val merged = TripMergeStrategy.merge(candidate, existing)

        assertEquals(0.73f, merged.appliedRateSnapshot)
        assertEquals(8, merged.vehicleId)
        assertEquals("Client visit", merged.purposeText)
    }

    @Test
    fun `merge combines metadata with expected precedence`() {
        val existing = testDrive(id = 1, start = 1000L, end = 2000L).copy(
            trackingMetadata = TrackingMetadataEmbedded(
                detectionSource = DetectionSource.AUTO,
                confidenceScore = 0.82f,
                batteryLevelAtStart = 55,
                batteryLevelAtEnd = 32,
                wasBatterySaverActive = true,
            )
        )
        val candidate = testDrive(id = 0, start = 900L, end = 2100L).copy(
            trackingMetadata = TrackingMetadataEmbedded(
                detectionSource = DetectionSource.MANUAL,
                confidenceScore = 0.95f,
                batteryLevelAtStart = 40,
                batteryLevelAtEnd = 21,
                wasBatterySaverActive = false,
            )
        )

        val merged = TripMergeStrategy.merge(candidate, existing)
        val metadata = merged.trackingMetadata

        assertNotNull(metadata)
        assertEquals(55, metadata?.batteryLevelAtStart)
        assertEquals(21, metadata?.batteryLevelAtEnd)
        assertEquals(true, metadata?.wasBatterySaverActive)
        assertEquals(0.95f, metadata?.confidenceScore)
        assertEquals(DetectionSource.AUTO, metadata?.detectionSource)
    }

    @Test
    fun `recordMergeAudit writes trip merged event`() = runTest {
        val auditRepository = mockk<AuditRepository>(relaxed = true)

        TripMergeStrategy.recordMergeAudit(
            survivorId = 9,
            consumedTripId = 3,
            actorUid = "u1",
            auditRepository = auditRepository,
        )

        coVerify {
            auditRepository.recordEvent(
                tripId = 9,
                actorUid = "u1",
                eventType = com.example.mileagetracker.data.local.entity.AuditEventType.TRIP_MERGED,
                fieldName = "merged_trip_id",
                oldValue = "3",
                newValue = "9",
            )
        }
    }
}
