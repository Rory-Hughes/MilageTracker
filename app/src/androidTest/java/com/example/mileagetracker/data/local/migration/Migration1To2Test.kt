package com.example.mileagetracker.data.local.migration/*
package com.example.mileagetracker.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mileagetracker.data.local.MileageDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration tests for [Migration1To2].
 *
 * Each test:
 * 1. Opens a version-1 database via [MigrationTestHelper.createDatabase].
 * 2. Inserts a representative legacy row using raw SQL (the v1 schema,
 *    so only camelCase column names exist).
 * 3. Runs the migration via [MigrationTestHelper.runMigrationsAndValidate].
 * 4. Queries the migrated database and asserts the expected values.
 *
 * [MigrationTestHelper.runMigrationsAndValidate] automatically validates
 * that the post-migration schema hash matches the Room-generated schema
 * for version 2, so structural correctness is verified implicitly.
 *
 * ## Test database name
 * A fixed name (`migration-test`) is used so the helper can locate the
 * exported schema JSON that Room generates at compile time.  The JSON
 * must be present at `schemas/` in the test assets (configured via
 * `room.schemaLocation` in the app's `build.gradle`).
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MileageDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    companion object {
        private const val TEST_DB = "migration-test"

        // Fixed epoch used across all tests for predictable assertions
        private const val LEGACY_TIMESTAMP = 1_700_000_000_000L  // Nov 14 2023
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 1. Schema upgrade
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * The migration must complete without throwing and the resulting
     * schema must match the Room-generated hash for version 2.
     * [runMigrationsAndValidate] handles the hash check automatically.
     */
    @Test
    fun migration1To2_schemaUpgradeCompletesSuccessfully() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = "Business", appliedRate = 0.70))
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).close()
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 2. Legacy rows remain readable
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * All five renamed columns must be readable under their new
     * snake_case names after migration.
     */
    @Test
    fun migration1To2_legacyColumnRenamesAreReadable() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                insertLegacyDrive(
                    purposeCategory = "Business",
                    appliedRate     = 0.70,
                    startLocation   = "Home",
                    endLocation     = "Office",
                    distanceKm      = 12.5,
                    dateString      = "2023-11-14",
                    timeString      = "08:30",
                )
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query(
                """
                    SELECT start_location, end_location, distance_km, date_string, time_string
                    FROM drives
                """
                ).use { c ->
                assertNotNull(c)
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("Home",        c.getString(c.getColumnIndexOrThrow("start_location")))
                assertEquals("Office",      c.getString(c.getColumnIndexOrThrow("end_location")))
                assertEquals(12.5f,         c.getFloat(c.getColumnIndexOrThrow("distance_km")))
                assertEquals("2023-11-14",  c.getString(c.getColumnIndexOrThrow("date_string")))
                assertEquals("08:30",       c.getString(c.getColumnIndexOrThrow("time_string")))
            }
        }
    }

    /** Existing row count must be identical after migration (no rows dropped). */
    @Test
    fun migration1To2_rowCountIsPreserved() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            repeat(5) { i ->
                db.execSQL(insertLegacyDrive(purposeCategory = "Business", appliedRate = 0.70 + i))
            }
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT COUNT(*) FROM drives").use { c ->
                c.moveToFirst()
                assertEquals(5, c.getInt(0))
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 3. trip_category backfill
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun migration1To2_tripCategory_businessMapsToBusinessEnum() {
        assertTripCategoryBackfill(legacyValue = "Business", expectedEnum = "BUSINESS")
    }

    @Test
    fun migration1To2_tripCategory_personalMapsToPersonalEnum() {
        assertTripCategoryBackfill(legacyValue = "Personal", expectedEnum = "PERSONAL")
    }

    @Test
    fun migration1To2_tripCategory_unclassifiedMapsToUnclassifiedEnum() {
        assertTripCategoryBackfill(legacyValue = "Unclassified", expectedEnum = "UNCLASSIFIED")
    }

    @Test
    fun migration1To2_tripCategory_unknownValueDefaultsToUnclassified() {
        assertTripCategoryBackfill(legacyValue = "SomethingRandom", expectedEnum = "UNCLASSIFIED")
    }

    @Test
    fun migration1To2_tripCategory_nullValueDefaultsToUnclassified() {
        assertTripCategoryBackfill(legacyValue = null, expectedEnum = "UNCLASSIFIED")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 4. classification_status backfill
    // ───────────────────────────────────────────────────────────────────────────

    /** A classified (Business/Personal) legacy drive becomes USER_CONFIRMED. */
    @Test
    fun migration1To2_classificationStatus_classifiedBecomesUserConfirmed() {
        assertClassificationStatusBackfill(
            legacyPurposeCategory  = "Business",
            expectedStatus         = "USER_CONFIRMED",
        )
    }

    /** An unclassified legacy drive becomes PENDING. */
    @Test
    fun migration1To2_classificationStatus_unclassifiedBecomesPending() {
        assertClassificationStatusBackfill(
            legacyPurposeCategory  = "Unclassified",
            expectedStatus         = "PENDING",
        )
    }

    /** Personal also maps to USER_CONFIRMED (it was a deliberate choice). */
    @Test
    fun migration1To2_classificationStatus_personalBecomesUserConfirmed() {
        assertClassificationStatusBackfill(
            legacyPurposeCategory  = "Personal",
            expectedStatus         = "USER_CONFIRMED",
        )
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 5. applied_rate_snapshot backfill
    // ───────────────────────────────────────────────────────────────────────────

    /** appliedRate is copied verbatim into applied_rate_snapshot. */
    @Test
    fun migration1To2_appliedRateSnapshot_copiedFromLegacyAppliedRate() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = "Business", appliedRate = 0.73))
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT applied_rate_snapshot FROM drives").use { c ->
                c.moveToFirst()
                assertEquals(0.73f, c.getFloat(c.getColumnIndexOrThrow("applied_rate_snapshot")), 0.001f)
            }
        }
    }

    /** NULL appliedRate is coalesced to 0.0, not left null (column is NOT NULL). */
    @Test
    fun migration1To2_appliedRateSnapshot_nullAppliedRateCoalescesToZero() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = "Unclassified", appliedRate = null))
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT applied_rate_snapshot FROM drives").use { c ->
                c.moveToFirst()
                assertEquals(0.0f, c.getFloat(c.getColumnIndexOrThrow("applied_rate_snapshot")), 0.001f)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 6. tracking_source default
    // ───────────────────────────────────────────────────────────────────────────

    /** Every migrated legacy row must carry tracking_source = 'LEGACY_IMPORT'. */
    @Test
    fun migration1To2_trackingSource_allRowsAreTaggedLegacyImport() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = "Business",     appliedRate = 0.70))
            db.execSQL(insertLegacyDrive(purposeCategory = "Personal",     appliedRate = 0.0))
            db.execSQL(insertLegacyDrive(purposeCategory = "Unclassified", appliedRate = null))
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT tracking_source FROM drives").use { c ->
                while (c.moveToNext()) {
                    assertEquals(
                        "LEGACY_IMPORT",
                        c.getString(c.getColumnIndexOrThrow("tracking_source")),
                    )
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 7. Timestamp backfills
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * started_at, created_at, and updated_at must all equal the legacy
     * timestamp column value after migration.
     */
    @Test
    fun migration1To2_timestampColumns_backfilledFromLegacyTimestamp() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = "Business", appliedRate = 0.70))
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT started_at, created_at, updated_at, timestamp FROM drives").use { c ->
                c.moveToFirst()
                val ts = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                assertEquals(ts, c.getLong(c.getColumnIndexOrThrow("started_at")))
                assertEquals(ts, c.getLong(c.getColumnIndexOrThrow("created_at")))
                assertEquals(ts, c.getLong(c.getColumnIndexOrThrow("updated_at")))
                assertEquals(LEGACY_TIMESTAMP, ts)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 8. New tables are created
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * All four tables introduced in v2 must exist and be queryable
     * after migration (a SELECT returning 0 rows with no exception
     * proves the table exists with the correct schema).
     */
    @Test
    fun migration1To2_newTables_allFourTablesExistAfterMigration() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            for (table in listOf("vehicles", "trip_audit", "cra_rates", "trip_attachments")) {
                db.query("SELECT * FROM $table LIMIT 0").use { c ->
                    assertNotNull("Table '$table' should exist after migration", c)
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Builds a v1 INSERT statement using camelCase column names
     * exactly as the old schema stored them.
     */
    private fun insertLegacyDrive(
        purposeCategory: String?,
        appliedRate: Double?,
        startLocation: String = "Start St",
        endLocation: String   = "End Ave",
        distanceKm: Double    = 5.0,
        dateString: String    = "2023-11-14",
        timeString: String    = "09:00",
    ): String {
        val purposeVal  = if (purposeCategory != null) "'$purposeCategory'" else "NULL"
        val rateVal     = appliedRate?.toString() ?: "NULL"
        return """
            INSERT INTO drives
                (timestamp, dateString, timeString, startLocation, endLocation,
                 distanceKm, purposeCategory, appliedRate)
            VALUES
                ($LEGACY_TIMESTAMP, '$dateString', '$timeString', '$startLocation',
                 '$endLocation', $distanceKm, $purposeVal, $rateVal)
        """.trimIndent()
    }

    private fun assertTripCategoryBackfill(
        legacyValue: String?,
        expectedEnum: String,
    ) {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = legacyValue, appliedRate = null))
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT trip_category FROM drives").use { c ->
                c.moveToFirst()
                assertEquals(expectedEnum, c.getString(c.getColumnIndexOrThrow("trip_category")))
            }
        }
    }

    private fun assertClassificationStatusBackfill(
        legacyPurposeCategory: String?,
        expectedStatus: String,
    ) {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(insertLegacyDrive(purposeCategory = legacyPurposeCategory, appliedRate = null))
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2).use { db ->
            db.query("SELECT classification_status FROM drives").use { c ->
                c.moveToFirst()
                assertEquals(expectedStatus, c.getString(c.getColumnIndexOrThrow("classification_status")))
            }
        }
    }
}
*/
