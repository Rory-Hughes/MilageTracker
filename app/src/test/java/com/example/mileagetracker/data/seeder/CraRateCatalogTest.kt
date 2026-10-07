package com.example.mileagetracker.data.seeder

import com.example.mileagetracker.domain.model.CraRateRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CraRateCatalogTest {

    @Test
    fun `resolve returns provincial rate as is`() {
        val rate = CraRateCatalog.resolve(2025, CraRateRegion.PROVINCES)
        assertEquals(2025, rate?.taxYear)
        assertEquals(72, rate?.firstTier)
        assertEquals(66, rate?.additional)
    }

    @Test
    fun `resolve applies territorial supplement`() {
        val rate = CraRateCatalog.resolve(2025, CraRateRegion.TERRITORIES)
        assertEquals(2025, rate?.taxYear)
        // Provincial 72 + 4 = 76
        assertEquals(76, rate?.firstTier)
        // Provincial 66 + 4 = 70
        assertEquals(70, rate?.additional)
    }

    @Test
    fun `resolve returns null for unknown year`() {
        assertNull(CraRateCatalog.resolve(1999, CraRateRegion.PROVINCES))
    }
}
