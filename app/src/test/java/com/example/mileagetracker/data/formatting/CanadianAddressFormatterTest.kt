package com.example.mileagetracker.data.formatting

import com.example.mileagetracker.domain.model.CanadianAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class CanadianAddressFormatterTest {

    private val address = CanadianAddress(
        street = "123 Main St",
        city = "Saint John",
        provinceAbbreviation = "NB",
        postalCode = "E2L 4Z6"
    )

    @Test
    fun `formatForExport returns single line with double space before postal code`() {
        val expected = "123 Main St, Saint John, NB  E2L 4Z6"
        assertEquals(expected, CanadianAddressFormatter.formatForExport(address))
    }

    @Test
    fun `formatMultiLine returns expected lines`() {
        val expected = "123 Main St\nSaint John, NB  E2L 4Z6\nCanada"
        assertEquals(expected, CanadianAddressFormatter.formatMultiLine(address))
    }

    @Test
    fun `normalizeRawLocationForExport normalizes postal codes in strings`() {
        val raw = "456 Oak Ave Toronto ON m5v2h1"
        val expected = "456 Oak Ave Toronto ON M5V 2H1"
        assertEquals(expected, CanadianAddressFormatter.normalizeRawLocationForExport(raw))
    }

    @Test
    fun `formatForExport handles partial addresses`() {
        val partial = CanadianAddress(
            street = "",
            city = "Toronto",
            provinceAbbreviation = "ON",
            postalCode = "M5V 2H1"
        )
        assertEquals("Toronto, ON  M5V 2H1", CanadianAddressFormatter.formatForExport(partial))
    }

    @Test
    fun `formatForExport returns empty string when all fields are blank`() {
        val blank = CanadianAddress(street = " ", city = "", provinceAbbreviation = " ", postalCode = "")
        assertEquals("", CanadianAddressFormatter.formatForExport(blank))
    }

    @Test
    fun `formatMultiLine omits country when includeCountry is false`() {
        val expected = "123 Main St\nSaint John, NB  E2L 4Z6"
        assertEquals(
            expected,
            CanadianAddressFormatter.formatMultiLine(address, includeCountry = false),
        )
    }

    @Test
    fun `normalizeRawLocationForExport returns trimmed raw string when no postal code exists`() {
        val raw = "   100 King Street West, Toronto   "
        assertEquals(
            "100 King Street West, Toronto",
            CanadianAddressFormatter.normalizeRawLocationForExport(raw),
        )
    }

    @Test
    fun `normalizeRawLocationForExport returns empty string for blank input`() {
        assertEquals("", CanadianAddressFormatter.normalizeRawLocationForExport("   "))
    }

    @Test
    fun `formatForExport trims fields and uppercases province abbreviation`() {
        val messy = CanadianAddress(
            street = "  77 Bloor St W ",
            city = " Toronto ",
            provinceAbbreviation = " on ",
            postalCode = "m5s2b1",
        )

        val result = CanadianAddressFormatter.formatForExport(messy)

        assertEquals("77 Bloor St W, Toronto, ON  M5S 2B1", result)
    }

    @Test
    fun `formatMultiLine returns only country when address lines are blank`() {
        val blank = CanadianAddress(street = "", city = "", provinceAbbreviation = "", postalCode = "")

        val result = CanadianAddressFormatter.formatMultiLine(blank, includeCountry = true)

        assertEquals("Canada", result)
    }

    @Test
    fun `normalizeRawLocationForExport normalizes multiple postal codes in one string`() {
        val raw = "Drop A: h2x1y4; Drop B: v6b 1a1"

        val result = CanadianAddressFormatter.normalizeRawLocationForExport(raw)

        assertEquals("Drop A: H2X 1Y4; Drop B: V6B 1A1", result)
    }
}
