package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Corrects the 2026 CRA prescribed provincial allowance rates.
 *
 * Only the known bad generated values are changed. User-created policies are
 * intentionally preserved.
 */
val Migration11To12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE cra_rates
            SET first_tier_rate_cents = 73,
                additional_km_rate_cents = 67
            WHERE tax_year = 2026
              AND first_tier_rate_cents = 74
              AND additional_km_rate_cents = 68
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE policies
            SET baseRate = 0.73,
                secondaryRate = 0.67
            WHERE policyName = 'Official CRA Rates (2026)'
              AND policyType = 'CRA_Standard'
              AND baseRate = 0.74
            """.trimIndent()
        )
    }
}
