package com.example.mileagetracker.data.local.entity

import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.model.TrackingMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingMetadataEmbeddedTest {

    @Test
    fun `toDomain returns null when detection source is missing`() {
        val embedded = TrackingMetadataEmbedded(
            detectionSource = null,
            confidenceScore = 0.9f,
            batteryLevelAtStart = 80,
            batteryLevelAtEnd = 70,
            wasBatterySaverActive = false,
        )

        assertNull(embedded.toDomain())
    }

    @Test
    fun `toDomain returns null when confidence score is missing`() {
        val embedded = TrackingMetadataEmbedded(
            detectionSource = DetectionSource.AUTO,
            confidenceScore = null,
            batteryLevelAtStart = 80,
            batteryLevelAtEnd = 70,
            wasBatterySaverActive = true,
        )

        assertNull(embedded.toDomain())
    }

    @Test
    fun `toDomain maps all fields when required values exist`() {
        val embedded = TrackingMetadataEmbedded(
            detectionSource = DetectionSource.MANUAL,
            confidenceScore = 1.0f,
            batteryLevelAtStart = 90,
            batteryLevelAtEnd = 50,
            wasBatterySaverActive = true,
        )

        val domain = embedded.toDomain()

        assertEquals(DetectionSource.MANUAL, domain?.detectionSource)
        assertEquals(1.0f, domain?.confidenceScore)
        assertEquals(90, domain?.batteryLevelAtStart)
        assertEquals(50, domain?.batteryLevelAtEnd)
        assertEquals(true, domain?.wasBatterySaverActive)
    }

    @Test
    fun `fromDomain maps all fields`() {
        val domain = TrackingMetadata(
            detectionSource = DetectionSource.AUTO,
            confidenceScore = 0.83f,
            batteryLevelAtStart = 67,
            batteryLevelAtEnd = 41,
            wasBatterySaverActive = false,
        )

        val embedded = TrackingMetadataEmbedded.fromDomain(domain)

        assertEquals(DetectionSource.AUTO, embedded.detectionSource)
        assertEquals(0.83f, embedded.confidenceScore)
        assertEquals(67, embedded.batteryLevelAtStart)
        assertEquals(41, embedded.batteryLevelAtEnd)
        assertEquals(false, embedded.wasBatterySaverActive)
    }

    @Test
    fun `toDomain keeps nullable battery fields when absent`() {
        val embedded = TrackingMetadataEmbedded(
            detectionSource = DetectionSource.AUTO,
            confidenceScore = 0.5f,
            batteryLevelAtStart = null,
            batteryLevelAtEnd = null,
            wasBatterySaverActive = false,
        )

        val domain = embedded.toDomain()

        assertEquals(DetectionSource.AUTO, domain?.detectionSource)
        assertEquals(0.5f, domain?.confidenceScore)
        assertEquals(null, domain?.batteryLevelAtStart)
        assertEquals(null, domain?.batteryLevelAtEnd)
    }

    @Test
    fun `fromDomain preserves null battery values`() {
        val domain = TrackingMetadata(
            detectionSource = DetectionSource.MANUAL,
            confidenceScore = 0.72f,
            batteryLevelAtStart = null,
            batteryLevelAtEnd = null,
            wasBatterySaverActive = true,
        )

        val embedded = TrackingMetadataEmbedded.fromDomain(domain)

        assertEquals(DetectionSource.MANUAL, embedded.detectionSource)
        assertEquals(0.72f, embedded.confidenceScore)
        assertEquals(null, embedded.batteryLevelAtStart)
        assertEquals(null, embedded.batteryLevelAtEnd)
        assertEquals(true, embedded.wasBatterySaverActive)
    }

    @Test
    fun `round trip fromDomain then toDomain keeps semantic values`() {
        val original = TrackingMetadata(
            detectionSource = DetectionSource.AUTO,
            confidenceScore = 0.88f,
            batteryLevelAtStart = 77,
            batteryLevelAtEnd = 33,
            wasBatterySaverActive = true,
        )

        val roundTrip = TrackingMetadataEmbedded.fromDomain(original).toDomain()

        assertEquals(original, roundTrip)
    }
}
