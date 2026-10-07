package com.example.mileagetracker.validation

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.validation.ErrorCode
import com.example.mileagetracker.domain.validation.OdometerValidator
import com.example.mileagetracker.domain.validation.ValidationResult
import com.example.mileagetracker.domain.validation.VehicleValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleValidatorTest {

    private lateinit var validator: VehicleValidator

    @Before
    fun setUp() {
        validator = VehicleValidator()
    }

    // Helpers

    private fun ValidationResult.errorCodes() =
        errors.map { it.code }.toSet()

    private fun validVehicle(
        displayName: String = "My Civic",
        ownerUid: String = "user-1",
        fiscalYear: Int = 2025,
        year: Int? = 2020,
        make: String? = "Honda",
        model: String? = "Civic",
        ownershipType: VehicleOwnershipType = VehicleOwnershipType.PERSONAL,
        isDefault: Boolean = false,
        isActive: Boolean = true,
    ) = VehicleEntity(
        displayName   = displayName,
        ownerUid      = ownerUid,
        fiscalYear    = fiscalYear,
        year          = year,
        make          = make,
        model         = model,
        ownershipType = ownershipType,
        isDefault     = isDefault,
        isActive      = isActive,
    )

    // validateIdentity

    @Test
    fun `validateIdentity passes valid vehicle`() {
        assertTrue(validator.validateIdentity(validVehicle()).isValid)
    }

    @Test
    fun `validateIdentity rejects blank displayName`() {
        val result = validator.validateIdentity(validVehicle(displayName = ""))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.BLANK_VEHICLE_NAME in result.errorCodes())
    }

    @Test
    fun `validateIdentity rejects displayName exceeding max length`() {
        val longName = "A".repeat(VehicleValidator.MAX_DISPLAY_NAME_LENGTH + 1)
        val result = validator.validateIdentity(validVehicle(displayName = longName))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.BLANK_VEHICLE_NAME in result.errorCodes())
    }

    @Test
    fun `validateIdentity accepts displayName at exact max length`() {
        val maxName = "A".repeat(VehicleValidator.MAX_DISPLAY_NAME_LENGTH)
        assertTrue(validator.validateIdentity(validVehicle(displayName = maxName)).isValid)
    }

    @Test
    fun `validateIdentity rejects vehicle year below minimum`() {
        val result = validator.validateIdentity(validVehicle(year = VehicleValidator.MIN_VEHICLE_YEAR - 1))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_VEHICLE_YEAR in result.errorCodes())
    }

    @Test
    fun `validateIdentity rejects vehicle year above maximum`() {
        val result = validator.validateIdentity(validVehicle(year = VehicleValidator.MAX_VEHICLE_YEAR + 1))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_VEHICLE_YEAR in result.errorCodes())
    }

    @Test
    fun `validateIdentity accepts null year`() {
        assertTrue(validator.validateIdentity(validVehicle(year = null)).isValid)
    }

    @Test
    fun `validateIdentity rejects blank make when provided`() {
        val result = validator.validateIdentity(validVehicle(make = ""))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    @Test
    fun `validateIdentity accepts null make`() {
        assertTrue(validator.validateIdentity(validVehicle(make = null)).isValid)
    }

    @Test
    fun `validateIdentity rejects blank model when provided`() {
        val result = validator.validateIdentity(validVehicle(model = ""))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_REQUIRED_FIELD in result.errorCodes())
    }

    @Test
    fun `validateIdentity accepts null model`() {
        assertTrue(validator.validateIdentity(validVehicle(model = null)).isValid)
    }

    // validateOwnership

    @Test
    fun `validateOwnership passes valid vehicle`() {
        assertTrue(validator.validateOwnership(validVehicle()).isValid)
    }

    @Test
    fun `validateOwnership rejects blank ownerUid`() {
        val result = validator.validateOwnership(validVehicle(ownerUid = ""))
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MISSING_OWNER_UID in result.errorCodes())
    }

    @Test
    fun `validateOwnership rejects fiscalYear below minimum`() {
        val result = validator.validateOwnership(
            validVehicle(fiscalYear = OdometerValidator.MIN_FISCAL_YEAR - 1)
        )
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_FISCAL_YEAR in result.errorCodes())
    }

    @Test
    fun `validateOwnership rejects fiscalYear above maximum`() {
        val result = validator.validateOwnership(
            validVehicle(fiscalYear = OdometerValidator.MAX_FISCAL_YEAR + 1)
        )
        assertFalse(result.isValid)
        assertTrue(ErrorCode.INVALID_FISCAL_YEAR in result.errorCodes())
    }

    @Test
    fun `validateOwnership accepts all VehicleOwnershipType values`() {
        VehicleOwnershipType.entries.forEach { type ->
            assertTrue(
                "Expected $type to be valid",
                validator.validateOwnership(validVehicle(ownershipType = type)).isValid,
            )
        }
    }

    // validateDefaultInvariant

    @Test
    fun `validateDefaultInvariant passes empty list`() {
        assertTrue(validator.validateDefaultInvariant(emptyList()).isValid)
    }

    @Test
    fun `validateDefaultInvariant passes list with no defaults`() {
        val vehicles = listOf(
            validVehicle(displayName = "Car A", isDefault = false),
            validVehicle(displayName = "Car B", isDefault = false),
        )
        assertTrue(validator.validateDefaultInvariant(vehicles).isValid)
    }

    @Test
    fun `validateDefaultInvariant passes list with exactly one default`() {
        val vehicles = listOf(
            validVehicle(displayName = "Car A", isDefault = true),
            validVehicle(displayName = "Car B", isDefault = false),
            validVehicle(displayName = "Car C", isDefault = false),
        )
        assertTrue(validator.validateDefaultInvariant(vehicles).isValid)
    }

    @Test
    fun `validateDefaultInvariant rejects list with two defaults`() {
        val vehicles = listOf(
            validVehicle(displayName = "Car A", isDefault = true),
            validVehicle(displayName = "Car B", isDefault = true),
        )
        val result = validator.validateDefaultInvariant(vehicles)
        assertFalse(result.isValid)
        assertTrue(ErrorCode.MULTIPLE_DEFAULT_VEHICLES in result.errorCodes())
    }

    @Test
    fun `validateDefaultInvariant rejects list with all defaults`() {
        val vehicles = (1..3).map { i ->
            validVehicle(displayName = "Car $i", isDefault = true)
        }
        val result = validator.validateDefaultInvariant(vehicles)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size) // single consolidated error
    }

    @Test
    fun `validateDefaultInvariant error message names both defaulted vehicles`() {
        val vehicles = listOf(
            validVehicle(displayName = "Civic",  isDefault = true),
            validVehicle(displayName = "Corolla", isDefault = true),
        )
        val result = validator.validateDefaultInvariant(vehicles)
        val message = result.errors.first().message
        assertTrue(message.contains("Civic"))
        assertTrue(message.contains("Corolla"))
    }

    // validateAll

    @Test
    fun `validateAll passes fully valid vehicle`() {
        assertTrue(validator.validateAll(validVehicle()).isValid)
    }

    @Test
    fun `validateAll accumulates errors from identity and ownership`() {
        val vehicle = validVehicle(displayName = "", ownerUid = "")
        val result = validator.validateAll(vehicle)
        assertFalse(result.isValid)
        val codes = result.errorCodes()
        assertTrue(ErrorCode.BLANK_VEHICLE_NAME in codes)
        assertTrue(ErrorCode.MISSING_OWNER_UID in codes)
    }
}

