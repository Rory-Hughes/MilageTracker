package com.example.mileagetracker.validation

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.validation.DriveValidator
import com.example.mileagetracker.domain.validation.ErrorCode
import com.example.mileagetracker.domain.validation.ValidationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveValidatorTest {

    private lateinit var validator: DriveValidator

    @Before
    fun setUp() {
        validator = DriveValidator()
    }

    // Helpers

    private fun ValidationResult.errorCodes() =
        errors.map { it.code }.toSet()

    private val pastMillis = System.currentTimeMillis() - 3_600_000L // 1 hour ago

    /** Minimal valid drive that passes validateBasic. */
    private fun validBasicDrive() = DriveEntity(
        startedAt = pastMillis,
        startLocation = "123 Main St",
        endLocation = "456 Oak Ave",
        distanceKm = 10f,
        driverUid = "user-1",
        trackingSource = TrackingSource.AUTO_TRACKED,
    )

    /** Fully CRA-compliant drive that passes validateCraReady. */
    private fun craReadyDrive() = validBasicDrive().copy(
        endedAt = pastMillis + 1_800_000L,
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        vehicleId = 1,
        purposeText = "Client meeting",
        appliedRateSnapshot = 0.70f,
    )

    // validateBasic

    @Test
    fun `validateBasic passes for valid minimal drive`() {
        assertTrue(validator.validateBasic(validBasicDrive()).isValid)
    }

    @Test
    fun `validateBasic rejects negative distance`() {
        val drive = validBasicDrive().copy(distanceKm = -1f)
        val result = validator.validateBasic(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NEGATIVE_DISTANCE in result.errorCodes())
    }

    @Test
    fun `validateBasic rejects invalid start timestamp`() {
        val drive = validBasicDrive().copy(startedAt = 0L)
        val result = validator.validateBasic(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_TIMESTAMP in result.errorCodes())
    }

    @Test
    fun `validateBasic rejects blank driverUid`() {
        val drive = validBasicDrive().copy(driverUid = "")
        val result = validator.validateBasic(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    @Test
    fun `validateBasic rejects null driverUid`() {
        val drive = validBasicDrive().copy(driverUid = null)
        val result = validator.validateBasic(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    @Test
    fun `validateBasic rejects blank startLocation`() {
        val drive = validBasicDrive().copy(startLocation = "")
        assertFalse(validator.validateBasic(drive).isValid)
    }

    @Test
    fun `validateBasic rejects blank endLocation`() {
        val drive = validBasicDrive().copy(endLocation = "")
        assertFalse(validator.validateBasic(drive).isValid)
    }

    @Test
    fun `validateBasic accumulates multiple errors`() {
        val drive = validBasicDrive().copy(distanceKm = -1f, driverUid = null)
        val result = validator.validateBasic(drive)
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 2)
    }

    // validateTiming

    @Test
    fun `validateTiming passes for drive with no endedAt`() {
        assertTrue(validator.validateTiming(validBasicDrive()).isValid)
    }

    @Test
    fun `validateTiming passes for drive with valid endedAt`() {
        val drive = validBasicDrive().copy(endedAt = pastMillis + 30 * 60_000L)
        assertTrue(validator.validateTiming(drive).isValid)
    }

    @Test
    fun `validateTiming rejects future startedAt`() {
        val drive = validBasicDrive().copy(
            startedAt = System.currentTimeMillis() + DriveValidator.CLOCK_SKEW_TOLERANCE_MS + 1_000L
        )
        val result = validator.validateTiming(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.FUTURE_TIMESTAMP in result.errorCodes())
    }

    @Test
    fun `validateTiming rejects endedAt before startedAt`() {
        val drive = validBasicDrive().copy(endedAt = pastMillis - 60_000L)
        val result = validator.validateTiming(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.END_BEFORE_START in result.errorCodes())
    }

    @Test
    fun `validateTiming rejects trip duration exceeding 24 hours`() {
        val drive = validBasicDrive().copy(
            endedAt = pastMillis + DriveValidator.MAX_TRIP_DURATION_MS + 1_000L
        )
        val result = validator.validateTiming(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.TRIP_TOO_LONG in result.errorCodes())
    }

    @Test
    fun `validateTiming accepts trip at exactly 24-hour duration boundary`() {
        val drive = validBasicDrive().copy(
            endedAt = pastMillis + DriveValidator.MAX_TRIP_DURATION_MS
        )
        assertTrue(validator.validateTiming(drive).isValid)
    }

    // validateCraReady (trip completeness)

    @Test
    fun `validateCraReady passes fully compliant drive`() {
        assertTrue(validator.validateCraReady(craReadyDrive()).isValid)
    }

    @Test
    fun `validateCraReady rejects non-BUSINESS category`() {
        val drive = craReadyDrive().copy(tripCategory = TripCategory.PERSONAL)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NOT_BUSINESS_TRIP in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects unconfirmed classification`() {
        val drive = craReadyDrive().copy(classificationStatus = ClassificationStatus.PENDING)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NOT_USER_CONFIRMED in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects missing vehicleId`() {
        val drive = craReadyDrive().copy(vehicleId = null)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_VEHICLE in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects blank purposeText`() {
        val drive = craReadyDrive().copy(purposeText = "")
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_PURPOSE in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects null purposeText`() {
        val drive = craReadyDrive().copy(purposeText = null)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_PURPOSE in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects zero distance`() {
        val drive = craReadyDrive().copy(distanceKm = 0f)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.ZERO_DISTANCE in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects missing rate snapshot`() {
        val drive = craReadyDrive().copy(appliedRateSnapshot = 0f)
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_RATE in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects blank startLocation`() {
        val drive = craReadyDrive().copy(startLocation = "")
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    @Test
    fun `validateCraReady rejects blank endLocation`() {
        val drive = craReadyDrive().copy(endLocation = "")
        val result = validator.validateCraReady(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    // validateCategoryPurpose

    @Test
    fun `validateCategoryPurpose passes personal trip without purpose`() {
        val drive = validBasicDrive().copy(tripCategory = TripCategory.PERSONAL)
        assertTrue(validator.validateCategoryPurpose(drive).isValid)
    }

    @Test
    fun `validateCategoryPurpose rejects BUSINESS trip without purpose`() {
        val drive = validBasicDrive().copy(
            tripCategory = TripCategory.BUSINESS,
            purposeText = null,
        )
        val result = validator.validateCategoryPurpose(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.BUSINESS_REQUIRES_PURPOSE in result.errorCodes())
    }

    @Test
    fun `validateCategoryPurpose passes BUSINESS trip with purpose`() {
        val drive = validBasicDrive().copy(
            tripCategory = TripCategory.BUSINESS,
            purposeText = "Client visit",
        )
        assertTrue(validator.validateCategoryPurpose(drive).isValid)
    }

    @Test
    fun `validateCategoryPurpose rejects UNCLASSIFIED + USER_CONFIRMED`() {
        val drive = validBasicDrive().copy(
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.USER_CONFIRMED,
        )
        val result = validator.validateCategoryPurpose(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.CONFIRMED_UNCLASSIFIED in result.errorCodes())
    }

    @Test
    fun `validateCategoryPurpose rejects confidence outside 0-1 range`() {
        val drive = validBasicDrive().copy(classificationConfidence = 1.5f)
        val result = validator.validateCategoryPurpose(drive)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_CONFIDENCE in result.errorCodes())
    }

    @Test
    fun `validateCategoryPurpose accepts confidence at boundaries`() {
        assertTrue(
            validator.validateCategoryPurpose(validBasicDrive().copy(classificationConfidence = 0f)).isValid
        )
        assertTrue(
            validator.validateCategoryPurpose(validBasicDrive().copy(classificationConfidence = 1f)).isValid
        )
    }

    @Test
    fun `validateCategoryPurpose accepts null confidence`() {
        val drive = validBasicDrive().copy(classificationConfidence = null)
        assertTrue(validator.validateCategoryPurpose(drive).isValid)
    }

    // validateAll

    @Test
    fun `validateAll passes CRA-ready drive`() {
        assertTrue(validator.validateAll(craReadyDrive()).isValid)
    }

    @Test
    fun `validateAll accumulates errors from multiple rule sets`() {
        // Missing driverUid (basic) + future timestamp (timing)
        val futureTs = System.currentTimeMillis() + DriveValidator.CLOCK_SKEW_TOLERANCE_MS + 5_000L
        val drive = validBasicDrive().copy(
            driverUid = null,
            startedAt = futureTs,
        )
        val result = validator.validateAll(drive)
        assertFalse(result.isValid)
        val codes = result.errorCodes()
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in codes)
        assertTrue(ErrorCode.FUTURE_TIMESTAMP in codes)
    }
}
