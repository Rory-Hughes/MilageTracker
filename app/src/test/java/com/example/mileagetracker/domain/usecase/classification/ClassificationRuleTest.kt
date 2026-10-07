package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ClassificationRuleTest {

    private fun testDrive(
        km: Float = 10f,
        startedAt: Long = 1000L,
        endedAt: Long? = null
    ) = DriveEntity(
        id = 0,
        startedAt = startedAt,
        endedAt = endedAt,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = startedAt
    )

    private fun mondayAt(hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 1, hour, minute, 0) // June 1, 2026 is a Monday
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun saturdayAt(hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 6, hour, minute, 0) // June 6, 2026 is a Saturday
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `MorningCommute matches weekday morning drives`() {
        val start = mondayAt(8, 0) // 08:00
        val drive = testDrive(km = 10f, startedAt = start)
        val match = ClassificationRule.MorningCommute.evaluate(drive)
        assertNotNull(match)
        assertEquals(TripCategory.COMMUTE, match?.category)
    }

    @Test
    fun `MorningCommute ignores weekends`() {
        val start = saturdayAt(8, 0)
        val drive = testDrive(km = 10f, startedAt = start)
        assertNull(ClassificationRule.MorningCommute.evaluate(drive))
    }

    @Test
    fun `EveningCommute matches weekday evening drives`() {
        val start = mondayAt(17, 30) // 17:30
        val drive = testDrive(km = 10f, startedAt = start)
        val match = ClassificationRule.EveningCommute.evaluate(drive)
        assertNotNull(match)
        assertEquals(TripCategory.COMMUTE, match?.category)
    }

    @Test
    fun `WeekendPersonal matches weekend drives`() {
        val start = saturdayAt(12, 0)
        val drive = testDrive(km = 50f, startedAt = start)
        val match = ClassificationRule.WeekendPersonal.evaluate(drive)
        assertNotNull(match)
        assertEquals(TripCategory.PERSONAL, match?.category)
    }

    @Test
    fun `LongHaulWeekday matches long weekday drives`() {
        val start = mondayAt(12, 0)
        val drive = testDrive(km = 250f, startedAt = start)
        val match = ClassificationRule.LongHaulWeekday.evaluate(drive)
        assertNotNull(match)
        assertEquals(TripCategory.BUSINESS, match?.category)
    }

    @Test
    fun `TrackingArtefact flags short distance`() {
        val drive = testDrive(km = 0.5f)
        val match = ClassificationRule.TrackingArtefact.evaluate(drive)
        assertNotNull(match)
        assertNull(match?.category) // Flags for review
    }

    @Test
    fun `TrackingArtefact flags short duration`() {
        val start = mondayAt(12, 0)
        val end = start + 60_000 // 1 minute
        val drive = testDrive(km = 5f, startedAt = start, endedAt = end)
        val match = ClassificationRule.TrackingArtefact.evaluate(drive)
        assertNotNull(match)
        assertNull(match?.category)
    }
}
