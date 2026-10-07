package com.example.mileagetracker.seeder

import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRateRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CraRateCatalogTest {
    @Test
    fun `2026 provincial prescribed rates match official CRA values`() {
        val rate = CraRateCatalog.resolve(2026, CraRateRegion.PROVINCES)

        assertEquals(73, rate?.firstTier)
        assertEquals(67, rate?.additional)
    }

    @Test
    fun `territories receive four cent supplement for both tiers`() {
        val rate = CraRateCatalog.resolve(2026, CraRateRegion.TERRITORIES)

        assertEquals(77, rate?.firstTier)
        assertEquals(71, rate?.additional)
    }

    @Test
    fun `unknown year is unavailable instead of silently falling back`() {
        assertNull(CraRateCatalog.resolve(2027, CraRateRegion.PROVINCES))
    }
}
