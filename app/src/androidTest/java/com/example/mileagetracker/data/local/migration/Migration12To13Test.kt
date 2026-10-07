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
 * Instrumented migration tests for [Migration12To13].
 *
 * Version 13 adds two new tables:
 * - `active_tracking_sessions` — single-row (id=1) checkpoint for an in-flight trip.
 * - `active_route_points`      — GPS points for the active session with a CASCADE FK.
 *
 * Each test:
 * 1. Opens a version-12 database via [MigrationTestHelper.createDatabase].
 * 2. (Where relevant) inserts representative rows using raw SQL.
 * 3. Runs the migration via [MigrationTestHelper.runMigrationsAndValidate].
 * 4. [runMigrationsAndValidate] automatically validates the resulting schema
 *    against the Room-generated hash for version 13 — structural correctness
 *    is verified implicitly.
 */
@RunWith(AndroidJUnit4::class)
class Migration12To13Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MileageDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private companion object {
        const val TEST_DB = "migration-12-to-13"
    }

    // ── 1. Schema upgrade ──────────────────────────────────────────────────────

    @Test
    fun migration12To13_schemaValidatesSuccessfully() {
        helper.createDatabase(TEST_DB, 12).close()
        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).close()
    }

    // ── 2. Existing data is preserved ─────────────────────────────────────────

    @Test
    fun migration12To13_existingDrivesArePreserved() {
        helper.createDatabase(TEST_DB, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO drives
                    (started_at, start_location, end_location, distance_km,
                     driver_uid, trip_category, classification_status, tracking_source,
                     applied_rate_snapshot, created_at, updated_at)
                VALUES (1000, 'Home', 'Office', 10.5, 'u1', 'BUSINESS', 'PENDING', 'AUTO_TRACKED',
                        0.0, 1000, 1000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.query("SELECT COUNT(*) FROM drives").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    // ── 3. New tables exist and are queryable ─────────────────────────────────

    @Test
    fun migration12To13_activeTrackingSessionsTableExists() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.query("SELECT * FROM active_tracking_sessions LIMIT 0").use { cursor ->
                assertNotNull(cursor)
            }
        }
    }

    @Test
    fun migration12To13_activeRoutePointsTableExists() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.query("SELECT * FROM active_route_points LIMIT 0").use { cursor ->
                assertNotNull(cursor)
            }
        }
    }

    // ── 4. New tables accept inserts ──────────────────────────────────────────

    @Test
    fun migration12To13_canInsertAndReadActiveSession() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.execSQL(
                """
                INSERT INTO active_tracking_sessions
                    (id, owner_uid, started_at, detection_source,
                     battery_level_at_start, battery_saver_seen, is_manual)
                VALUES (1, 'u1', 1000, 'AUTO', 85, 0, 0)
                """.trimIndent()
            )

            db.query("SELECT owner_uid, started_at FROM active_tracking_sessions WHERE id = 1")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("u1", cursor.getString(0))
                    assertEquals(1000L, cursor.getLong(1))
                }
        }
    }

    // ── 5. FK between new tables: cascade from session to route points ────────

    @Test
    fun migration12To13_activeRoutePoints_cascadeDeletesWhenSessionDeleted() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.execSQL(
                """
                INSERT INTO active_tracking_sessions
                    (id, owner_uid, started_at, detection_source,
                     battery_level_at_start, battery_saver_seen, is_manual)
                VALUES (1, 'u1', 1000, 'AUTO', NULL, 0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO active_route_points
                    (session_id, lat, lng, altitude_metres, accuracy_metres,
                     speed_kph, timestamp_ms)
                VALUES (1, 45.0, -66.0, NULL, NULL, NULL, 2000)
                """.trimIndent()
            )

            // Verify point is linked
            db.query("SELECT COUNT(*) FROM active_route_points WHERE session_id = 1")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }

            // FK cascade: delete session removes its route points
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM active_tracking_sessions WHERE id = 1")

            db.query("SELECT COUNT(*) FROM active_route_points").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    // ── 6. Nullable column in new table ───────────────────────────────────────

    @Test
    fun migration12To13_activeSession_nullBatteryLevelAccepted() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13).use { db ->
            db.execSQL(
                """
                INSERT INTO active_tracking_sessions
                    (id, owner_uid, started_at, detection_source,
                     battery_level_at_start, battery_saver_seen, is_manual)
                VALUES (1, 'u1', 1000, 'MANUAL', NULL, 1, 1)
                """.trimIndent()
            )

            db.query("SELECT battery_level_at_start FROM active_tracking_sessions WHERE id = 1")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(true, cursor.isNull(0))
                }
        }
    }
}
