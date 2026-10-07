package com.example.mileagetracker.util

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class DriveDisplayModelTest {

    private fun testDrive(
        startedAt: Long = 1000L,
        km: Float = 12.35f,
        start: String = "Start",
        end: String = "End",
        category: TripCategory = TripCategory.BUSINESS,
        purpose: String? = null
    ) = DriveEntity(
        id = 1,
        startedAt = startedAt,
        endedAt = startedAt + 1000,
        startLocation = start,
        endLocation = end,
        distanceKm = km,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = category,
        purposeText = purpose,
        classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = startedAt
    )

    @Test
    fun `from converts entity fields correctly`() {
        // Set fixed locale for deterministic results
        Locale.setDefault(Locale.US)
        
        val cal = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 14, 8, 45, 0)
        }
        val drive = testDrive(startedAt = cal.timeInMillis, km = 12.35f, start = "A", end = "B")
        
        val model = DriveDisplayModel.from(drive)
        
        assertEquals("Jun 14", model.dateLabel)
        assertEquals("8:45 AM", model.timeLabel)
        assertEquals("12.4 km", model.distanceLabel)
        assertEquals("A \u2192 B", model.routeLabel)
        assertEquals("Business", model.categoryLabel)
    }

    @Test
    fun `from handles CUSTOM category with purpose`() {
        val drive = testDrive(category = TripCategory.CUSTOM, purpose = "Grocery")
        val model = DriveDisplayModel.from(drive)
        assertEquals("Grocery", model.categoryLabel)
    }

    @Test
    fun `from handles CUSTOM category without purpose`() {
        val drive = testDrive(category = TripCategory.CUSTOM, purpose = null)
        val model = DriveDisplayModel.from(drive)
        assertEquals("Custom", model.categoryLabel)
    }
}
