package com.example.mileagetracker.seeder

import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.seeder.CraRateSeeder
import org.junit.Assert.assertEquals
import org.junit.Test

class CraRateSeederPolicyConsistencyTest {
    @Test
    fun `default policy base rate matches latest CRA first-tier rate`() {
        val latestFirstTierRate = CraRateSeeder.KNOWN_RATES.last().firstTier / 100f
        assertEquals(
            latestFirstTierRate,
            MileageDatabase.DEFAULT_POLICY_BASE_RATE,
            0.0001f,
        )
    }
}
