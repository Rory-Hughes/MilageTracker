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
 * End-to-end chained migration tests covering the full 11 → 15 upgrade path.
 *
 * Each test applies all three migrations in sequence
 * ([Migration11To12] → [Migration12To13] → [Migration13To14] → [Migration14To15]) in a single
 * [MigrationTestHelper.runMigrationsAndValidate] call, which mirrors the
 * real upgrade path for a user jumping from v11 to the current build in one
 * install.
 *
 * [runMigrationsAndValidate] validates the final schema hash against the
 * Room-generated v15 JSON, so structural correctness is verified implicitly.
 *
 * ## Why a chain test?
 * Individual migration tests (12→13, 13→14) verify each migration in
 * isolation.  This test catches ordering bugs — e.g., a migration that
 * accidentally drops a column added by a prior migration, or an index name
 * collision — that only manifest when migrations run together.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MileageDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private companion object {
        const val TEST_DB = "migration-chain-11-to-15"
    }

    // ── 1. Full chain schema validation ───────────────────────────────────────

    @Test
    fun chainMigration11To15_schemaValidatesAtVersion15() {
        helper.createDatabase(TEST_DB, 11).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            Migration11To12, Migration12To13, Migration13To14, Migration14To15,
        ).close()
    }

    // ── 2. Data seeded at v11 survives the full chain ─────────────────────────

    @Test
    fun chainMigration11To15_existingDrivesAndCraRatesSurvive() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            // Drive row present in v11
            db.execSQL(
                """
                INSERT INTO drives
                    (started_at, start_location, end_location, distance_km,
                     driver_uid, trip_category, classification_status, tracking_source,
                     applied_rate_snapshot, created_at, updated_at)
                VALUES (1000, 'Home', 'Office', 12.5, 'u1', 'BUSINESS', 'USER_CONFIRMED', 'AUTO_TRACKED',
                        0.0, 1000, 1000)
                """.trimIndent()
            )
            // CRA rate present in v11
            db.execSQL(
                """
                INSERT INTO cra_rates
                    (tax_year, first_tier_rate_cents, first_tier_limit_km,
                     additional_km_rate_cents, source_label, effective_from)
                VALUES (2025, 70, 5000, 64, 'CRA IT522 2025', 100)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            Migration11To12, Migration12To13, Migration13To14, Migration14To15,
        ).use { db ->
            db.query("SELECT distance_km, driver_uid FROM drives").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(12.5f, cursor.getFloat(0), 0.001f)
                assertEquals("u1", cursor.getString(1))
            }
            db.query("SELECT tax_year FROM cra_rates").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(2025, cursor.getInt(0))
            }
        }
    }

    // ── 3. Migration11To12 data fix survives subsequent migrations ─────────────

    @Test
    fun chainMigration11To15_migration11To12DataFixIsApplied() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            // The bad 2026 rates that Migration11To12 corrects
            db.execSQL(
                """
                INSERT INTO cra_rates
                    (tax_year, first_tier_rate_cents, first_tier_limit_km,
                     additional_km_rate_cents, source_label, effective_from)
                VALUES (2026, 74, 5000, 68, 'CRA', 1)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            Migration11To12, Migration12To13, Migration13To14, Migration14To15,
        ).use { db ->
            db.query(
                "SELECT first_tier_rate_cents, additional_km_rate_cents FROM cra_rates WHERE tax_year = 2026"
            ).use { cursor ->
                cursor.moveToFirst()
                // Migration11To12 corrects 74→73 and 68→67
                assertEquals(73, cursor.getInt(0))
                assertEquals(67, cursor.getInt(1))
            }
        }
    }

    // ── 4. All tables from every migration exist in the final schema ───────────

    @Test
    fun chainMigration11To15_allExpectedTablesExist() {
        helper.createDatabase(TEST_DB, 11).close()

        val expectedTables = listOf(
            // Pre-existing (v11)
            "drives", "vehicles", "trip_audit", "cra_rates", "trip_attachments",
            "route_points", "buffered_trips", "odometer_captures",
            "user_classification_rules", "report_history", "vehicle_expenses",
            "approval_requests", "period_locks", "policy_templates",
            // Added by 12→13
            "active_tracking_sessions", "active_route_points",
            // Added by 13→14
            "sync_outbox", "sync_metadata", "sync_conflicts",
        )

        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            Migration11To12, Migration12To13, Migration13To14, Migration14To15,
        ).use { db ->
            for (table in expectedTables) {
                db.query("SELECT * FROM `$table` LIMIT 0").use { cursor ->
                    assertNotNull("Table '$table' must exist after chain migration 11→15", cursor)
                }
            }
        }
    }

    // ── 5. Cross-migration inserts: data from new tables co-exists ─────────────

    @Test
    fun chainMigration11To15_canInsertIntoAllNewTables() {
        helper.createDatabase(TEST_DB, 11).close()

        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            Migration11To12, Migration12To13, Migration13To14, Migration14To15,
        ).use { db ->
            // active_tracking_sessions (added in 12→13)
            db.execSQL(
                """
                INSERT INTO active_tracking_sessions
                    (id, owner_uid, started_at, detection_source,
                     battery_level_at_start, battery_saver_seen, is_manual)
                VALUES (1, 'u1', 1000, 'AUTO', NULL, 0, 0)
                """.trimIndent()
            )

            // sync_outbox (added in 13→14)
            db.execSQL(
                """
                INSERT INTO sync_outbox
                    (id, sync_id, owner_uid, entity_type, entity_id,
                     operation, payload_json, base_revision, created_at, attempt_count)
                VALUES ('item-1', 'sync-1', 'u1', 'DRIVE', 'drive-1',
                        'CREATE', '{}', 0, 2000, 0)
                """.trimIndent()
            )

            db.query("SELECT COUNT(*) FROM active_tracking_sessions").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM sync_outbox").use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }

            db.execSQL(
                """
                INSERT INTO user_classification_rules
                    (id, owner_uid, name, keyword, category, confidence, priority, is_enabled, created_at)
                VALUES (1, 'u1', 'Client', 'client', 'BUSINESS', 0.75, 10, 1, 3000)
                """.trimIndent()
            )
            db.query("SELECT owner_uid FROM user_classification_rules WHERE id = 1").use { c ->
                c.moveToFirst(); assertEquals("u1", c.getString(0))
            }
        }
    }
}
