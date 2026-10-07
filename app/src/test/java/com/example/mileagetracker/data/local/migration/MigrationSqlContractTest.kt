package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationSqlContractTest {

    @Test
    fun `migration 1 to 2 expands drives and creates phase one tables`() {
        val statements = runMigration(Migration1To2)

        assertHasSql(statements, "ALTER TABLE drives ADD COLUMN started_at INTEGER")
        assertHasSql(statements, "CREATE TABLE drives_new")
        assertHasSql(statements, "DROP TABLE drives")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS vehicles")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS trip_audit")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS cra_rates")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS trip_attachments")
    }

    @Test
    fun `migration 2 to 3 creates route points and metadata columns`() {
        val statements = runMigration(Migration2To3)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `route_points`")
        assertHasSql(statements, "index_route_points_trip_id_timestamp_ms")
        assertHasSql(statements, "ALTER TABLE `drives` ADD COLUMN `meta_detection_source`")
        assertHasSql(statements, "ALTER TABLE `drives` ADD COLUMN `meta_was_battery_saver_active`")
    }

    @Test
    fun `migration 3 to 4 creates buffered trips table`() {
        val statements = runMigration(Migration3To4)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `buffered_trips`")
    }

    @Test
    fun `migration 4 to 5 is no op`() {
        val statements = runMigration(Migration4To5)

        assertTrue(statements.isEmpty())
    }

    @Test
    fun `migration 5 to 6 creates odometer captures table and indexes`() {
        val statements = runMigration(Migration5To6)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `odometer_captures`")
        assertHasSql(statements, "index_odometer_captures_vehicle_id")
        assertHasSql(statements, "index_odometer_captures_vehicle_id_capture_date")
    }

    @Test
    fun `migration 6 to 7 creates user classification rules table`() {
        val statements = runMigration(Migration6To7)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `user_classification_rules`")
    }

    @Test
    fun `migration 7 to 8 creates report history table and indexes`() {
        val statements = runMigration(Migration7To8)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `report_history`")
        assertHasSql(statements, "index_report_history_year")
        assertHasSql(statements, "index_report_history_generated_at")
    }

    @Test
    fun `migration 8 to 9 creates vehicle expenses table and indexes`() {
        val statements = runMigration(Migration8To9)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `vehicle_expenses`")
        assertHasSql(statements, "index_vehicle_expenses_vehicle_id_date")
    }

    @Test
    fun `migration 9 to 10 creates approval and period lock tables`() {
        val statements = runMigration(Migration9To10)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `approval_requests`")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `period_locks`")
        assertHasSql(statements, "index_period_locks_year_month")
    }

    @Test
    fun `migration 10 to 11 creates policy templates table`() {
        val statements = runMigration(Migration10To11)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `policy_templates`")
    }

    @Test
    fun `migration 11 to 12 updates incorrect 2026 cra rates and policy`() {
        val statements = runMigration(Migration11To12)

        assertHasSql(statements, "UPDATE cra_rates")
        assertHasSql(statements, "tax_year = 2026")
        assertHasSql(statements, "UPDATE policies")
        assertHasSql(statements, "Official CRA Rates (2026)")
    }

    @Test
    fun `migration 12 to 13 creates active tracking checkpoint tables`() {
        val statements = runMigration(Migration12To13)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `active_tracking_sessions`")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `active_route_points`")
        assertHasSql(statements, "index_active_route_points_session_id_timestamp_ms")
    }

    @Test
    fun `migration 13 to 14 creates sync outbox metadata and conflicts tables`() {
        val statements = runMigration(Migration13To14)

        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `sync_outbox`")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `sync_metadata`")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `sync_conflicts`")
        assertHasSql(statements, "index_sync_conflicts_owner_uid_resolved_at")
    }

    @Test
    fun `migration 14 to 15 adds owner uid to user classification rules`() {
        val statements = runMigration(Migration14To15)

        assertHasSql(statements, "ALTER TABLE `user_classification_rules` RENAME TO `user_classification_rules_old`")
        assertHasSql(statements, "CREATE TABLE IF NOT EXISTS `user_classification_rules`")
        assertHasSql(statements, "`owner_uid` TEXT NOT NULL")
        assertHasSql(statements, "index_user_classification_rules_owner_uid_is_enabled_priority")
    }

    @Test
    fun `migration 15 to 16 adds odometer evidence metadata`() {
        val statements = runMigration(Migration15To16)

        assertHasSql(statements, "ALTER TABLE `odometer_captures` ADD COLUMN `tax_year`")
        assertHasSql(statements, "ALTER TABLE `odometer_captures` ADD COLUMN `boundary_type`")
        assertHasSql(statements, "strftime('%Y', `capture_date` / 1000, 'unixepoch')")
        assertHasSql(statements, "index_odometer_captures_vehicle_id_tax_year_boundary_type")
    }

    private fun runMigration(migration: Migration): List<String> {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)
        val statements = mutableListOf<String>()
        every { database.execSQL(capture(statements)) } returns Unit
        migration.migrate(database)
        return statements
    }

    private fun assertHasSql(statements: List<String>, fragment: String) {
        assertTrue(
            "Expected SQL fragment was not executed: $fragment",
            statements.any { statement -> statement.contains(fragment, ignoreCase = true) },
        )
    }
}
