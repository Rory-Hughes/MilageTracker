package com.example.mileagetracker.validation

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.validation.ErrorCode
import com.example.mileagetracker.domain.validation.OdometerValidator
import com.example.mileagetracker.domain.validation.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OdometerValidatorTest {

    private lateinit var validator: OdometerValidator

    @Before
    fun setUp() {
        validator = OdometerValidator()
    }

    // Helpers

    private fun ValidationResult.errorCodes() =
        errors.map { it.code }.toSet()

    private fun vehicleWith(
        opening: Float? = null,
        closing: Float? = null,
        fiscalYear: Int = 2025,
    ) = VehicleEntity(
        ownerUid = "user-1",
        displayName = "Test Car",
        fiscalYear = fiscalYear,
        ownershipType = VehicleOwnershipType.PERSONAL,
        openingOdometerKm = opening,
        closingOdometerKm = closing,
    )

    // validateReading

    @Test
    fun `validateReading accepts zero`() {
        assertTrue(validator.validateReading(0f).isValid)
    }

    @Test
    fun `validateReading accepts typical reading`() {
        assertTrue(validator.validateReading(150_000f).isValid)
    }

    @Test
    fun `validateReading accepts max plausible boundary`() {
        assertTrue(validator.validateReading(OdometerValidator.MAX_PLAUSIBLE_ODOMETER_KM).isValid)
    }

    @Test
    fun `validateReading rejects negative reading`() {
        val result = validator.validateReading(-1f)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NEGATIVE_ODOMETER in result.errorCodes())
    }

    @Test
    fun `validateReading rejects reading above max plausible`() {
        val result = validator.validateReading(OdometerValidator.MAX_PLAUSIBLE_ODOMETER_KM + 1f)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.ODOMETER_EXCEEDS_MAX in result.errorCodes())
    }

    @Test
    fun `validateReading uses provided fieldName in error`() {
        val result = validator.validateReading(-5f, "opening_odometer_km")
        assertEquals("opening_odometer_km", result.errors.first().field)
    }

    // validatePair

    @Test
    fun `validatePair accepts equal opening and closing`() {
        assertTrue(validator.validatePair(50_000f, 50_000f).isValid)
    }

    @Test
    fun `validatePair accepts normal annual span`() {
        assertTrue(validator.validatePair(10_000f, 30_000f).isValid)
    }

    @Test
    fun `validatePair rejects closing before opening`() {
        val result = validator.validatePair(30_000f, 20_000f)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.CLOSING_BEFORE_OPENING in result.errorCodes())
    }

    @Test
    fun `validatePair rejects annual span exceeding max`() {
        val result = validator.validatePair(
            openingKm = 0f,
            closingKm = OdometerValidator.MAX_ANNUAL_SPAN_KM + 1f,
        )
        assertFalse(result.isValid)
        assertTrue(ErrorCode.ANNUAL_SPAN_TOO_LARGE in result.errorCodes())
    }

    @Test
    fun `validatePair accepts span exactly at max boundary`() {
        assertTrue(
            validator.validatePair(0f, OdometerValidator.MAX_ANNUAL_SPAN_KM).isValid
        )
    }

    @Test
    fun `validatePair propagates individual reading errors`() {
        val result = validator.validatePair(-1f, 10_000f)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NEGATIVE_ODOMETER in result.errorCodes())
    }

    @Test
    fun `validatePair does not produce CLOSING_BEFORE_OPENING when opening is invalid`() {
        // Negative opening; closing > opening by absolute values.
        // CLOSING_BEFORE_OPENING should NOT fire because -1 < 10000.
        val result = validator.validatePair(-1f, 10_000f)
        assertFalse(ErrorCode.CLOSING_BEFORE_OPENING in result.errorCodes())
    }

    // validateFiscalYearCombination

    @Test
    fun `validateFiscalYearCombination accepts valid year with no odometer`() {
        assertTrue(validator.validateFiscalYearCombination(2025).isValid)
    }

    @Test
    fun `validateFiscalYearCombination accepts valid year with both odometers`() {
        assertTrue(
            validator.validateFiscalYearCombination(2025, 10_000f, 25_000f).isValid
        )
    }

    @Test
    fun `validateFiscalYearCombination rejects year below minimum`() {
        val result = validator.validateFiscalYearCombination(OdometerValidator.MIN_FISCAL_YEAR - 1)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_FISCAL_YEAR in result.errorCodes())
    }

    @Test
    fun `validateFiscalYearCombination rejects year above maximum`() {
        val result = validator.validateFiscalYearCombination(OdometerValidator.MAX_FISCAL_YEAR + 1)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_FISCAL_YEAR in result.errorCodes())
    }

    @Test
    fun `validateFiscalYearCombination rejects year more than one ahead of current`() {
        val futureYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 2
        val result = validator.validateFiscalYearCombination(futureYear)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.FUTURE_FISCAL_YEAR in result.errorCodes())
    }

    @Test
    fun `validateFiscalYearCombination validates only opening when closing is null`() {
        val result = validator.validateFiscalYearCombination(2025, openingKm = -1f)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.NEGATIVE_ODOMETER in result.errorCodes())
    }

    @Test
    fun `validateFiscalYearCombination validates only closing when opening is null`() {
        val result = validator.validateFiscalYearCombination(
            2025,
            closingKm = OdometerValidator.MAX_PLAUSIBLE_ODOMETER_KM + 1f
        )
        assertFalse(result.isValid)
        assertTrue(ErrorCode.ODOMETER_EXCEEDS_MAX in result.errorCodes())
    }

    // validateVehicle convenience

    @Test
    fun `validateVehicle delegates to fiscal year combination`() {
        val vehicle = vehicleWith(opening = 10_000f, closing = 25_000f, fiscalYear = 2025)
        assertTrue(validator.validateVehicle(vehicle).isValid)
    }

    @Test
    fun `validateVehicle surfaces pair error from vehicle entity`() {
        val vehicle = vehicleWith(opening = 30_000f, closing = 10_000f)
        val result = validator.validateVehicle(vehicle)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.CLOSING_BEFORE_OPENING in result.errorCodes())
    }
}
