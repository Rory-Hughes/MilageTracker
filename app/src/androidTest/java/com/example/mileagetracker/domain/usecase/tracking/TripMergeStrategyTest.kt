package com.example.mileagetracker.domain.usecase.tracking

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit-style tests for [TripMergeStrategy.merge].
 *
 * [TripMergeStrategy] is a pure `object` with no database dependency, so
 * these tests run on-device without a Room database — they verify each merge
 * rule described in the KDoc table:
 *
 * | Field               | Rule |
 * |---------------------|------|
 * | `id`                | Always from [existing] |
 * | `startedAt`         | min(candidate, existing) |
 * | `endedAt`           | max(candidate, existing) |
 * | `distanceKm`        | max(candidate, existing) |
 * | `startLocation`     | Trip with earlier `startedAt` |
 * | `endLocation`       | Trip with later `endedAt` |
 * | `tripCategory`      | Existing wins if classified; candidate wins if PENDING/NEEDS_REVIEW |
 * | `notes`             | Concatenate if both non-null; fall back to non-null |
 * | `vehicleId`         | Existing wins if non-null; candidate otherwise |
 * | `appliedRateSnapshot` | Existing wins if non-zero; candidate otherwise |
 * | `updatedAt`         | Set to current time |
 * | `trackingMetadata`  | batteryStart=max, batteryEnd=min, saver=OR |
 */
@RunWith(AndroidJUnit4::class)
class TripMergeStrategyTest {

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun drive(
        id: Int = 0,
        startedAt: Long = 1_000L,
        endedAt: Long? = 5_000L,
        distanceKm: Float = 10f,
        startLocation: String = "A",
        endLocation: String = "B",
        driverUid: String = "u1",
        tripCategory: TripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
        notes: String? = null,
        vehicleId: Int? = null,
        appliedRateSnapshot: Float = 0f,
        trackingMetadata: TrackingMetadataEmbedded? = null,
    ) = DriveEntity(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceKm = distanceKm,
        startLocation = startLocation,
        endLocation = endLocation,
        driverUid = driverUid,
        tripCategory = tripCategory,
        classificationStatus = classificationStatus,
        trackingSource = TrackingSource.AUTO_TRACKED,
        notes = notes,
        vehicleId = vehicleId,
        appliedRateSnapshot = appliedRateSnapshot,
        trackingMetadata = trackingMetadata,
    )

    // ── id always from existing ────────────────────────────────────────────────

    @Test
    fun merge_idAlwaysFromExisting() {
        val existing = drive(id = 42)
        val candidate = drive(id = 0)

        val merged = TripMergeStrategy.merge(candidate, existing)
        assertEquals(42, merged.id)
    }

    // ── startedAt: min of both ────────────────────────────────────────────────

    @Test
    fun merge_startedAt_earlierCandidateWins() {
        val candidate = drive(startedAt = 500L)
        val existing = drive(startedAt = 1_000L)

        assertEquals(500L, TripMergeStrategy.merge(candidate, existing).startedAt)
    }

    @Test
    fun merge_startedAt_earlierExistingWins() {
        val candidate = drive(startedAt = 2_000L)
        val existing = drive(startedAt = 1_000L)

        assertEquals(1_000L, TripMergeStrategy.merge(candidate, existing).startedAt)
    }

    // ── endedAt: max of both ──────────────────────────────────────────────────

    @Test
    fun merge_endedAt_laterCandidateWins() {
        val candidate = drive(endedAt = 9_000L)
        val existing = drive(endedAt = 5_000L)

        assertEquals(9_000L, TripMergeStrategy.merge(candidate, existing).endedAt)
    }

    @Test
    fun merge_endedAt_laterExistingWins() {
        val candidate = drive(endedAt = 3_000L)
        val existing = drive(endedAt = 8_000L)

        assertEquals(8_000L, TripMergeStrategy.merge(candidate, existing).endedAt)
    }

    // ── distanceKm: max of both ───────────────────────────────────────────────

    @Test
    fun merge_distanceKm_candidateLargerWins() {
        val candidate = drive(distanceKm = 25f)
        val existing = drive(distanceKm = 10f)

        assertEquals(25f, TripMergeStrategy.merge(candidate, existing).distanceKm)
    }

    @Test
    fun merge_distanceKm_existingLargerWins() {
        val candidate = drive(distanceKm = 5f)
        val existing = drive(distanceKm = 18f)

        assertEquals(18f, TripMergeStrategy.merge(candidate, existing).distanceKm)
    }

    // ── startLocation: from trip with earlier startedAt ───────────────────────

    @Test
    fun merge_startLocation_fromEarlierStartedAt_candidateEarlier() {
        val candidate = drive(startedAt = 100L, startLocation = "Depot")
        val existing = drive(startedAt = 500L, startLocation = "Home")

        assertEquals("Depot", TripMergeStrategy.merge(candidate, existing).startLocation)
    }

    @Test
    fun merge_startLocation_fromEarlierStartedAt_existingEarlier() {
        val candidate = drive(startedAt = 500L, startLocation = "Depot")
        val existing = drive(startedAt = 100L, startLocation = "Home")

        assertEquals("Home", TripMergeStrategy.merge(candidate, existing).startLocation)
    }

    // ── endLocation: from trip with later endedAt ──────────────────────────────

    @Test
    fun merge_endLocation_fromLaterEndedAt_candidateLater() {
        val candidate = drive(endedAt = 9_000L, endLocation = "Airport")
        val existing = drive(endedAt = 5_000L, endLocation = "Office")

        assertEquals("Airport", TripMergeStrategy.merge(candidate, existing).endLocation)
    }

    @Test
    fun merge_endLocation_fromLaterEndedAt_existingLater() {
        val candidate = drive(endedAt = 3_000L, endLocation = "Airport")
        val existing = drive(endedAt = 7_000L, endLocation = "Office")

        assertEquals("Office", TripMergeStrategy.merge(candidate, existing).endLocation)
    }

    // ── tripCategory: existing wins if classified, candidate if PENDING ────────

    @Test
    fun merge_tripCategory_existingClassified_existingWins() {
        val candidate = drive(
            tripCategory = TripCategory.PERSONAL,
            classificationStatus = ClassificationStatus.USER_CONFIRMED
        )
        val existing = drive(
            tripCategory = TripCategory.BUSINESS,
            classificationStatus = ClassificationStatus.USER_CONFIRMED
        )

        val merged = TripMergeStrategy.merge(candidate, existing)
        assertEquals(TripCategory.BUSINESS, merged.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, merged.classificationStatus)
    }

    @Test
    fun merge_tripCategory_existingPending_candidateWins() {
        val candidate = drive(
            tripCategory = TripCategory.MEDICAL,
            classificationStatus = ClassificationStatus.USER_CONFIRMED
        )
        val existing = drive(
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.PENDING
        )

        val merged = TripMergeStrategy.merge(candidate, existing)
        assertEquals(TripCategory.MEDICAL, merged.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, merged.classificationStatus)
    }

    @Test
    fun merge_tripCategory_existingNeedsReview_candidateWins() {
        val candidate = drive(
            tripCategory = TripCategory.BUSINESS,
            classificationStatus = ClassificationStatus.USER_CONFIRMED
        )
        val existing = drive(
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.NEEDS_REVIEW
        )

        val merged = TripMergeStrategy.merge(candidate, existing)
        assertEquals(TripCategory.BUSINESS, merged.tripCategory)
    }

    // ── notes: concatenation ───────────────────────────────────────────────────

    @Test
    fun merge_notes_bothNonNull_concatenatesWithSeparator() {
        val candidate = drive(notes = "Candidate note")
        val existing = drive(notes = "Existing note")

        val merged = TripMergeStrategy.merge(candidate, existing)
        assertEquals("Existing note | Candidate note", merged.notes)
    }

    @Test
    fun merge_notes_onlyExistingNonNull_usesExisting() {
        val candidate = drive(notes = null)
        val existing = drive(notes = "Existing only")

        assertEquals("Existing only", TripMergeStrategy.merge(candidate, existing).notes)
    }

    @Test
    fun merge_notes_onlyCandidateNonNull_usesCandidate() {
        val candidate = drive(notes = "Candidate only")
        val existing = drive(notes = null)

        assertEquals("Candidate only", TripMergeStrategy.merge(candidate, existing).notes)
    }

    @Test
    fun merge_notes_bothNull_remainsNull() {
        assertNull(TripMergeStrategy.merge(drive(notes = null), drive(notes = null)).notes)
    }

    // ── vehicleId: existing wins if non-null ──────────────────────────────────

    @Test
    fun merge_vehicleId_existingNonNull_existingWins() {
        val candidate = drive(vehicleId = 99)
        val existing = drive(vehicleId = 7)

        assertEquals(7, TripMergeStrategy.merge(candidate, existing).vehicleId)
    }

    @Test
    fun merge_vehicleId_existingNull_candidateWins() {
        val candidate = drive(vehicleId = 42)
        val existing = drive(vehicleId = null)

        assertEquals(42, TripMergeStrategy.merge(candidate, existing).vehicleId)
    }

    @Test
    fun merge_vehicleId_bothNull_remainsNull() {
        assertNull(TripMergeStrategy.merge(drive(vehicleId = null), drive(vehicleId = null)).vehicleId)
    }

    // ── appliedRateSnapshot: existing wins if non-zero ─────────────────────────

    @Test
    fun merge_appliedRateSnapshot_existingNonZero_existingWins() {
        val candidate = drive(appliedRateSnapshot = 0.85f)
        val existing = drive(appliedRateSnapshot = 0.73f)

        assertEquals(0.73f, TripMergeStrategy.merge(candidate, existing).appliedRateSnapshot)
    }

    @Test
    fun merge_appliedRateSnapshot_existingZero_candidateWins() {
        val candidate = drive(appliedRateSnapshot = 0.73f)
        val existing = drive(appliedRateSnapshot = 0f)

        assertEquals(0.73f, TripMergeStrategy.merge(candidate, existing).appliedRateSnapshot)
    }

    // ── updatedAt is set to current wall-clock time ───────────────────────────

    @Test
    fun merge_updatedAt_setToCurrentTime() {
        val before = System.currentTimeMillis()
        val merged = TripMergeStrategy.merge(drive(), drive())
        val after = System.currentTimeMillis()

        assertTrue(
            "updatedAt (${merged.updatedAt}) must be in [$before, $after]",
            merged.updatedAt in before..after,
        )
    }

    // ── trackingMetadata: battery start=max, battery end=min, saver=OR ────────

    @Test
    fun merge_metadata_bothNonNull_mergingRulesApplied() {
        val metaA = TrackingMetadataEmbedded(
            batteryLevelAtStart = 80,
            batteryLevelAtEnd = 50,
            wasBatterySaverActive = false,
            confidenceScore = 0.6f,
        )
        val metaB = TrackingMetadataEmbedded(
            batteryLevelAtStart = 90,
            batteryLevelAtEnd = 30,
            wasBatterySaverActive = true,
            confidenceScore = 0.9f,
        )

        val merged = TripMergeStrategy.merge(
            candidate = drive(trackingMetadata = metaA),
            existing = drive(trackingMetadata = metaB),
        )

        val meta = merged.trackingMetadata!!
        // Battery start: max(80, 90) = 90 (highest % = started with most charge)
        assertEquals(90, meta.batteryLevelAtStart)
        // Battery end: min(50, 30) = 30 (lowest % = depleted most)
        assertEquals(30, meta.batteryLevelAtEnd)
        // Battery saver: false OR true = true
        assertTrue(meta.wasBatterySaverActive)
        // Confidence: max(0.6, 0.9) = 0.9
        assertEquals(0.9f, meta.confidenceScore!!, 0.001f)
    }

    @Test
    fun merge_metadata_onlyExistingNonNull_usesExisting() {
        val meta = TrackingMetadataEmbedded(batteryLevelAtStart = 75)
        val merged = TripMergeStrategy.merge(
            candidate = drive(trackingMetadata = null),
            existing = drive(trackingMetadata = meta),
        )
        assertEquals(75, merged.trackingMetadata!!.batteryLevelAtStart)
    }

    @Test
    fun merge_metadata_onlyCandidateNonNull_usesCandidate() {
        val meta = TrackingMetadataEmbedded(batteryLevelAtStart = 60)
        val merged = TripMergeStrategy.merge(
            candidate = drive(trackingMetadata = meta),
            existing = drive(trackingMetadata = null),
        )
        assertEquals(60, merged.trackingMetadata!!.batteryLevelAtStart)
    }

    @Test
    fun merge_metadata_bothNull_remainsNull() {
        val merged = TripMergeStrategy.merge(
            candidate = drive(trackingMetadata = null),
            existing = drive(trackingMetadata = null),
        )
        assertNull(merged.trackingMetadata)
    }
}
