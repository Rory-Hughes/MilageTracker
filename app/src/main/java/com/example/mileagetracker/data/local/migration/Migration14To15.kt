package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 14 -> 15.
 *
 * Scopes user-defined classification rules to an owning account so that
 * sign-out, restore, and account deletion can reason about them safely.
 */
object Migration14To15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_classification_rules` RENAME TO `user_classification_rules_old`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_classification_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `owner_uid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `keyword` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `priority` INTEGER NOT NULL,
                `is_enabled` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `user_classification_rules` (
                `id`, `owner_uid`, `name`, `keyword`, `category`,
                `confidence`, `priority`, `is_enabled`, `created_at`
            )
            SELECT
                `id`,
                'LEGACY_USER',
                `name`,
                `keyword`,
                `category`,
                `confidence`,
                `priority`,
                `is_enabled`,
                `created_at`
            FROM `user_classification_rules_old`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `user_classification_rules_old`")
        db.execSQL(
            """
                CREATE INDEX IF NOT EXISTS `index_user_classification_rules_owner_uid` 
                ON `user_classification_rules` (`owner_uid`)
            """
        )
        db.execSQL(
            """
                CREATE INDEX IF NOT EXISTS `index_user_classification_rules_owner_uid_priority_name` 
                ON `user_classification_rules` (`owner_uid`, `priority`, `name`)
            """
        )
        db.execSQL(
            """
                CREATE INDEX IF NOT EXISTS `index_user_classification_rules_owner_uid_is_enabled_priority` 
                ON `user_classification_rules` (`owner_uid`, `is_enabled`, `priority`)
            """
        )
    }
}
