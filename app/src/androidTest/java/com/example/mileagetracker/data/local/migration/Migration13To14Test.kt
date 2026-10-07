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
 * Instrumented migration tests for [Migration13To14].
 *
 * Version 14 adds three offline-sync tables:
 * - `sync_outbox`   — pending write operations queued for Firebase upload.
 * - `sync_metadata` — per-entity revision tracking; unique index on
 *                     (owner_uid, entity_type, local_entity_id).
 * - `sync_conflicts` — visible conflict queue; `resolved_at` is nullable
 *                      (null = unresolved).
 *
 * [runMigrationsAndValidate] automatically validates the resulting schema
 * against the Room-generated hash for version 14.
 */
@RunWith(AndroidJUnit4::class)
class Migration13To14Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MileageDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private companion object {
        const val TEST_DB = "migration-13-to-14"
    }

    // ── 1. Schema upgrade ──────────────────────────────────────────────────────

    @Test
    fun migration13To14_schemaValidatesSuccessfully() {
        helper.createDatabase(TEST_DB, 13).close()
        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).close()
    }

    // ── 2. Existing data is preserved ─────────────────────────────────────────

    @Test
    fun migration13To14_existingDrivesArePreserved() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                """
                INSERT INTO drives
                    (started_at, start_location, end_location, distance_km,
                     driver_uid, trip_category, classification_status, tracking_source,
                     applied_rate_snapshot, created_at, updated_at)
                VALUES (1000, 'A', 'B', 5.0, 'u1', 'BUSINESS', 'PENDING', 'AUTO_TRACKED',
                        0.0, 1000, 1000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).use { db ->
            db.query("SELECT COUNT(*) FROM drives").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    // ── 3. All three new tables exist ─────────────────────────────────────────

    @Test
    fun migration13To14_allThreeNewTablesExist() {
        helper.createDatabase(TEST_DB, 13).close()

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).use { db ->
            for (table in listOf("sync_outbox", "sync_metadata", "sync_conflicts")) {
                db.query("SELECT * FROM $table LIMIT 0").use { cursor ->
                    assertNotNull("Table '$table' must exist after migration 13→14", cursor)
                }
            }
        }
    }

    // ── 4. sync_outbox: insert + read ─────────────────────────────────────────

    @Test
    fun migration13To14_canInsertAndReadSyncOutboxRow() {
        helper.createDatabase(TEST_DB, 13).close()

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).use { db ->
            db.execSQL(
                """
                INSERT INTO sync_outbox
                    (id, sync_id, owner_uid, entity_type, entity_id,
                     operation, payload_json, base_revision, created_at, attempt_count)
                VALUES ('item-1', 'sync-1', 'u1', 'DRIVE', 'drive-99',
                        'CREATE', '{}', 0, 1000, 0)
                """.trimIndent()
            )

            db.query("SELECT operation, owner_uid FROM sync_outbox WHERE id = 'item-1'")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("CREATE", cursor.getString(0))
                    assertEquals("u1", cursor.getString(1))
                }
        }
    }

    // ── 5. sync_metadata: unique index enforced ───────────────────────────────

    @Test
    fun migration13To14_syncMetadata_uniqueIndexEnforced() {
        helper.createDatabase(TEST_DB, 13).close()

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).use { db ->
            db.execSQL(
                """
                INSERT INTO sync_metadata
                    (sync_id, owner_uid, entity_type, local_entity_id,
                     revision, sync_state, updated_at, deleted_at)
                VALUES ('m1', 'u1', 'DRIVE', 'drive-1', 1, 'SYNCED', 1000, NULL)
                """.trimIndent()
            )

            // Duplicate (owner_uid, entity_type, local_entity_id) must fail.
            var threw = false
            try {
                db.execSQL(
                    """
                    INSERT INTO sync_metadata
                        (sync_id, owner_uid, entity_type, local_entity_id,
                         revision, sync_state, updated_at, deleted_at)
                    VALUES ('m2', 'u1', 'DRIVE', 'drive-1', 2, 'PENDING', 2000, NULL)
                    """.trimIndent()
                )
            } catch (_: Exception) {
                threw = true
            }
            assertEquals("Duplicate compound key should be rejected", true, threw)
        }
    }

    // ── 6. sync_conflicts: nullable resolved_at ───────────────────────────────

    @Test
    fun migration13To14_syncConflicts_nullResolvedAtAccepted() {
        helper.createDatabase(TEST_DB, 13).close()

        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migration13To14).use { db ->
            db.execSQL(
                """
                INSERT INTO sync_conflicts
                    (id, sync_id, owner_uid, entity_type, entity_id,
                     local_payload_json, remote_payload_json,
                     local_revision, remote_revision, created_at, resolved_at)
                VALUES ('c1', 'sync-1', 'u1', 'DRIVE', 'drive-1',
                        '{"local":1}', '{"remote":2}', 1, 2, 1000, NULL)
                """.trimIndent()
            )

            db.query("SELECT resolved_at FROM sync_conflicts WHERE id = 'c1'")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(true, cursor.isNull(0))
                }
        }
    }
}
