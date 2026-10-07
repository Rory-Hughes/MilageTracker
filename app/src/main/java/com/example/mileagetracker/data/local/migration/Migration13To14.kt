package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the offline-first Firebase sync outbox and visible conflict queue.
 */
val Migration13To14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createSyncOutboxTable(db)
        createSyncMetadataTable(db)
        createSyncConflictsTable(db)
    }
}

private fun createSyncOutboxTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_outbox` (
            `id` TEXT NOT NULL,
            `sync_id` TEXT NOT NULL,
            `owner_uid` TEXT NOT NULL,
            `entity_type` TEXT NOT NULL,
            `entity_id` TEXT NOT NULL,
            `operation` TEXT NOT NULL,
            `payload_json` TEXT NOT NULL,
            `base_revision` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            `attempt_count` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSqlStatements(
        "CREATE INDEX IF NOT EXISTS `index_sync_outbox_owner_uid` " +
            "ON `sync_outbox` (`owner_uid`)",
        "CREATE INDEX IF NOT EXISTS `index_sync_outbox_owner_uid_created_at` " +
            "ON `sync_outbox` (`owner_uid`, `created_at`)",
    )
}

private fun createSyncMetadataTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_metadata` (
            `sync_id` TEXT NOT NULL,
            `owner_uid` TEXT NOT NULL,
            `entity_type` TEXT NOT NULL,
            `local_entity_id` TEXT NOT NULL,
            `revision` INTEGER NOT NULL,
            `sync_state` TEXT NOT NULL,
            `updated_at` INTEGER NOT NULL,
            `deleted_at` INTEGER,
            PRIMARY KEY(`sync_id`)
        )
        """.trimIndent()
    )
    db.execSqlStatements(
        "CREATE UNIQUE INDEX IF NOT EXISTS " +
            "`index_sync_metadata_owner_uid_entity_type_local_entity_id` " +
            "ON `sync_metadata` (`owner_uid`, `entity_type`, `local_entity_id`)",
        "CREATE INDEX IF NOT EXISTS `index_sync_metadata_owner_uid_sync_state` " +
            "ON `sync_metadata` (`owner_uid`, `sync_state`)",
    )
}

private fun createSyncConflictsTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_conflicts` (
            `id` TEXT NOT NULL,
            `sync_id` TEXT NOT NULL,
            `owner_uid` TEXT NOT NULL,
            `entity_type` TEXT NOT NULL,
            `entity_id` TEXT NOT NULL,
            `local_payload_json` TEXT NOT NULL,
            `remote_payload_json` TEXT NOT NULL,
            `local_revision` INTEGER NOT NULL,
            `remote_revision` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            `resolved_at` INTEGER,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSqlStatements(
        "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_owner_uid` " +
            "ON `sync_conflicts` (`owner_uid`)",
        "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_owner_uid_resolved_at` " +
            "ON `sync_conflicts` (`owner_uid`, `resolved_at`)",
    )
}

private fun SupportSQLiteDatabase.execSqlStatements(vararg statements: String) {
    statements.forEach(::execSQL)
}
