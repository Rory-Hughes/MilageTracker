package com.example.mileagetracker.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds explicit tax-year and CRA boundary metadata to odometer captures.
 */
object Migration15To16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `odometer_captures` ADD COLUMN `tax_year` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `odometer_captures` ADD COLUMN `boundary_type` TEXT NOT NULL DEFAULT 'OTHER'"
        )
        db.execSQL(
            """
            UPDATE `odometer_captures`
            SET `tax_year` = CAST(strftime('%Y', `capture_date` / 1000, 'unixepoch') AS INTEGER)
            WHERE `tax_year` = 0
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_odometer_captures_vehicle_id_tax_year_boundary_type`
            ON `odometer_captures` (`vehicle_id`, `tax_year`, `boundary_type`)
            """.trimIndent()
        )
    }
}
