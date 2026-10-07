package com.example.mileagetracker.data.local.converter

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomTypeConvertersTest {

    private val converters = RoomTypeConverters()

    @Test
    fun `TripCategory conversion works`() {
        assertEquals("BUSINESS", converters.tripCategoryToString(TripCategory.BUSINESS))
        assertEquals(TripCategory.BUSINESS, converters.stringToTripCategory("BUSINESS"))
        assertNull(converters.tripCategoryToString(null))
        assertNull(converters.stringToTripCategory(null))
    }

    @Test
    fun `ClassificationStatus conversion works`() {
        assertEquals("PENDING", converters.classificationStatusToString(ClassificationStatus.PENDING))
        assertEquals(ClassificationStatus.PENDING, converters.stringToClassificationStatus("PENDING"))
        assertNull(converters.classificationStatusToString(null))
        assertNull(converters.stringToClassificationStatus(null))
    }

    @Test
    fun `TrackingSource conversion works`() {
        assertEquals("AUTO_TRACKED", converters.trackingSourceToString(TrackingSource.AUTO_TRACKED))
        assertEquals(TrackingSource.AUTO_TRACKED, converters.stringToTrackingSource("AUTO_TRACKED"))
        assertNull(converters.trackingSourceToString(null))
        assertNull(converters.stringToTrackingSource(null))
    }

    @Test
    fun `VehicleOwnershipType conversion works`() {
        assertEquals("PERSONAL", converters.vehicleOwnershipTypeToString(VehicleOwnershipType.PERSONAL))
        assertEquals(VehicleOwnershipType.PERSONAL, converters.stringToVehicleOwnershipType("PERSONAL"))
        assertNull(converters.vehicleOwnershipTypeToString(null))
        assertNull(converters.stringToVehicleOwnershipType(null))
    }

    @Test
    fun `AuditEventType conversion works`() {
        assertEquals("DRIVE_CREATED", converters.auditEventTypeToString(AuditEventType.DRIVE_CREATED))
        assertEquals(AuditEventType.DRIVE_CREATED, converters.stringToAuditEventType("DRIVE_CREATED"))
        assertNull(converters.auditEventTypeToString(null))
        assertNull(converters.stringToAuditEventType(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringToTripCategory throws on invalid value`() {
        converters.stringToTripCategory("INVALID")
    }
}
