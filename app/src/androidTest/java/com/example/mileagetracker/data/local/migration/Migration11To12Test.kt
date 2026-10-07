package com.example.mileagetracker.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mileagetracker.data.local.MileageDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MileageDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration_correctsKnownBad2026CraRows() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                """
                INSERT INTO cra_rates
                    (tax_year, first_tier_rate_cents, first_tier_limit_km,
                     additional_km_rate_cents, source_label, effective_from)
                VALUES (2026, 74, 5000, 68, 'CRA', 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration11To12).use { db ->
            db.query(
                "SELECT first_tier_rate_cents, additional_km_rate_cents FROM cra_rates WHERE tax_year = 2026"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(73, cursor.getInt(0))
                assertEquals(67, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration_updatesGeneratedDefaultButPreservesCustomPolicies() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                """
                INSERT INTO policies (policyName, policyType, baseRate, secondaryRate, thresholdKm)
                VALUES ('Official CRA Rates (2026)', 'CRA_Standard', 0.74, 0.68, 5000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO policies (policyName, policyType, baseRate, secondaryRate, thresholdKm)
                VALUES ('My Custom Rate', 'CUSTOM', 0.74, 0.68, 5000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration11To12).use { db ->
            db.query("SELECT policyName, baseRate, secondaryRate FROM policies ORDER BY policyName").use { cursor ->
                cursor.moveToFirst()
                assertEquals("My Custom Rate", cursor.getString(0))
                assertEquals(0.74f, cursor.getFloat(1), 0.001f)
                assertEquals(0.68f, cursor.getFloat(2), 0.001f)
                cursor.moveToNext()
                assertEquals("Official CRA Rates (2026)", cursor.getString(0))
                assertEquals(0.73f, cursor.getFloat(1), 0.001f)
                assertEquals(0.67f, cursor.getFloat(2), 0.001f)
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-11-to-12"
    }
}
