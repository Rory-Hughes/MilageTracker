package com.example.mileagetracker.data.validation

import com.example.mileagetracker.domain.model.CanadianAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanadianAddressValidatorTest {

    @Test
    fun `validateProvince accepts valid codes`() {
        assertEquals(CanadianAddressValidator.ValidationResult.Valid, CanadianAddressValidator.validateProvince("ON"))
        assertEquals(CanadianAddressValidator.ValidationResult.Valid, CanadianAddressValidator.validateProvince("BC"))
        assertEquals(CanadianAddressValidator.ValidationResult.Valid, CanadianAddressValidator.validateProvince("QC"))
    }

    @Test
    fun `validateProvince rejects invalid codes`() {
        assertTrue(CanadianAddressValidator.validateProvince("XX") is CanadianAddressValidator.ValidationResult.Invalid)
        assertTrue(CanadianAddressValidator.validateProvince("on") is CanadianAddressValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validatePostalCode accepts valid input`() {
        assertEquals(
            CanadianAddressValidator.ValidationResult.Valid,
            CanadianAddressValidator.validatePostalCode("A1A 1A1"),
        )
        assertEquals(
            CanadianAddressValidator.ValidationResult.Valid,
            CanadianAddressValidator.validatePostalCode("k1a0b1"),
        )
    }

    @Test
    fun `validatePostalCode rejects invalid input`() {
        assertTrue(
            CanadianAddressValidator.validatePostalCode("123456") is
                CanadianAddressValidator.ValidationResult.Invalid,
        )
    }

    @Test
    fun `validate checks all fields`() {
        val address = CanadianAddress(
            street = "123 Main St",
            city = "Toronto",
            provinceAbbreviation = "ON",
            postalCode = "M5V 2H1"
        )
        val results = CanadianAddressValidator.validate(address)
        assertTrue(results.values.all { it == CanadianAddressValidator.ValidationResult.Valid })

        val invalidAddress = address.copy(street = "", provinceAbbreviation = "XX")
        val invalidResults = CanadianAddressValidator.validate(invalidAddress)
        assertTrue(invalidResults["street"] is CanadianAddressValidator.ValidationResult.Invalid)
        assertTrue(invalidResults["province"] is CanadianAddressValidator.ValidationResult.Invalid)
    }
}
