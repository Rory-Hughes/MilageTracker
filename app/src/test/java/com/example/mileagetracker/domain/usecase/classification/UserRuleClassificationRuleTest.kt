package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.UserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UserRuleClassificationRuleTest {

    @Test
    fun `name includes rule id and title`() {
        val rule = UserRuleClassificationRule(
            userRule(id = 7, name = "Client Alpha")
        )

        assertEquals("UserRule:7:Client Alpha", rule.name)
    }

    @Test
    fun `priority mirrors user rule priority`() {
        val rule = UserRuleClassificationRule(userRule(priority = 12))

        assertEquals(12, rule.priority)
    }

    @Test
    fun `evaluate matches keyword in start location case-insensitively`() {
        val rule =
            UserRuleClassificationRule(
                userRule(keyword = "Airport", category = TripCategory.BUSINESS, confidence = 0.84f)
            )
        val drive = drive(startLocation = "to airport terminal")

        val match = rule.evaluate(drive)

        assertNotNull(match)
        assertEquals(TripCategory.BUSINESS, match?.category)
        assertEquals(0.84f, match?.confidence)
    }

    @Test
    fun `evaluate matches keyword in end location case-insensitively`() {
        val rule = UserRuleClassificationRule(userRule(keyword = "depot", category = TripCategory.BUSINESS))
        val drive = drive(startLocation = "Home", endLocation = "Main DEPOT")

        val match = rule.evaluate(drive)

        assertNotNull(match)
        assertEquals(TripCategory.BUSINESS, match?.category)
    }

    @Test
    fun `evaluate matches keyword in purpose text case-insensitively`() {
        val rule = UserRuleClassificationRule(userRule(keyword = "invoice", category = TripCategory.BUSINESS))
        val drive = drive(
            startLocation = "Home",
            endLocation = "Office",
            purposeText = "Deliver INVOICE package",
        )

        val match = rule.evaluate(drive)

        assertNotNull(match)
        assertEquals(TripCategory.BUSINESS, match?.category)
    }

    @Test
    fun `evaluate matches keyword in end location and purpose text`() {
        val rule =
            UserRuleClassificationRule(
                userRule(keyword = "clinic", category = TripCategory.MEDICAL, confidence = 0.92f)
            )
        val endLocationDrive = drive(endLocation = "Downtown Clinic", purposeText = null)
        val purposeDrive = drive(endLocation = "Main Street", purposeText = "Follow-up CLINIC visit")

        val endMatch = rule.evaluate(endLocationDrive)
        val purposeMatch = rule.evaluate(purposeDrive)

        assertEquals(TripCategory.MEDICAL, endMatch?.category)
        assertEquals(TripCategory.MEDICAL, purposeMatch?.category)
    }

    @Test
    fun `evaluate returns null when keyword is absent across fields`() {
        val rule = UserRuleClassificationRule(userRule(keyword = "warehouse"))
        val drive = drive(
            startLocation = "Home",
            endLocation = "Office",
            purposeText = "Weekly planning",
        )

        val match = rule.evaluate(drive)

        assertNull(match)
    }

    private fun userRule(
        id: Int = 5,
        name: String = "Rule",
        keyword: String = "client",
        category: TripCategory = TripCategory.BUSINESS,
        confidence: Float = 0.75f,
        priority: Int = 50,
    ) = UserRule(
        id = id,
        name = name,
        keyword = keyword,
        category = category,
        confidence = confidence,
        priority = priority,
        isEnabled = true,
        createdAt = 1_700_000_000_000L,
    )

    private fun drive(
        startLocation: String = "Start",
        endLocation: String = "End",
        purposeText: String? = "Purpose",
    ) = DriveEntity(
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
        startLocation = startLocation,
        endLocation = endLocation,
        distanceKm = 12.0f,
        driverUid = "uid-1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        purposeText = purposeText,
        trackingSource = TrackingSource.AUTO_TRACKED,
    )
}
