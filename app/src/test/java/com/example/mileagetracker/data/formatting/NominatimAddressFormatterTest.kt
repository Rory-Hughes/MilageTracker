package com.example.mileagetracker.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimAddressFormatterTest {

    @Test
    fun `full address with house number is reduced to street, city, province, country`() {
        val address = mapOf(
            "house_number" to "2638",
            "road" to "Westfield Road",
            "neighbourhood" to "Morna",
            "suburb" to "Ketepec",
            "city" to "City of Saint John",
            "county" to "Saint John County",
            "state" to "New Brunswick",
            "ISO3166-2-lvl4" to "CA-NB",
            "postcode" to "E2M 6L4",
            "country" to "Canada",
        )

        assertEquals(
            "2638 Westfield Road, Saint John, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `address without house number omits the number cleanly`() {
        val address = mapOf(
            "road" to "Brunswick Place",
            "city" to "City of Saint John",
            "ISO3166-2-lvl4" to "CA-NB",
            "country" to "Canada",
        )

        assertEquals(
            "Brunswick Place, Saint John, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `amenity name is ignored in favour of the road`() {
        // Nominatim puts amenity names (school, etc.) in display_name's first
        // slot, but the structured object keeps the road separate.
        val address = mapOf(
            "amenity" to "Harbourview High School",
            "road" to "Douglas Avenue",
            "city" to "City of Saint John",
            "ISO3166-2-lvl4" to "CA-NB",
            "country" to "Canada",
        )

        assertEquals(
            "Douglas Avenue, Saint John, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `province falls back to state name when ISO code is absent`() {
        val address = mapOf(
            "road" to "King Street",
            "city" to "Fredericton",
            "state" to "New Brunswick",
            "country" to "Canada",
        )

        assertEquals(
            "King Street, Fredericton, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `bilingual state name still resolves to a code`() {
        val address = mapOf(
            "road" to "Rue Principale",
            "city" to "Moncton",
            "state" to "New Brunswick / Nouveau-Brunswick",
            "country" to "Canada",
        )

        assertEquals(
            "Rue Principale, Moncton, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `town is used when city is absent`() {
        val address = mapOf(
            "road" to "Main Street",
            "town" to "Sussex",
            "ISO3166-2-lvl4" to "CA-NB",
            "country" to "Canada",
        )

        assertEquals(
            "Main Street, Sussex, NB, Canada",
            NominatimAddressFormatter.format(address),
        )
    }

    @Test
    fun `empty address map yields null so caller can fall back`() {
        assertNull(NominatimAddressFormatter.format(emptyMap()))
    }

    @Test
    fun `unknown province name is dropped rather than guessed`() {
        val address = mapOf(
            "road" to "Some Road",
            "city" to "Somewhere",
            "state" to "Atlantis",
            "country" to "Canada",
        )

        // No province component — better to omit than emit garbage.
        assertEquals(
            "Some Road, Somewhere, Canada",
            NominatimAddressFormatter.format(address),
        )
    }
}
