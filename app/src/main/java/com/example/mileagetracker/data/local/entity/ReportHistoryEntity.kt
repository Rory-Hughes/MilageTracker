package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists metadata for every successfully generated logbook export.
 *
 * ## CRA retention requirement
 * CRA expects taxpayers to retain supporting records for at least 6 years
 * from the end of the tax year to which they relate.  [ReportHistoryRepository]
 * enforces this by blocking deletion of rows where
 * `generatedAt > (now - 6 years)`.
 *
 * [ReportHistoryCleanupWorker] (Task 5.5) prunes rows older than **7 years**
 * (one year beyond the CRA minimum) so that storage does not grow unbounded.
 *
 * ## Column layout
 * | Column            | Type    | Notes                                      |
 * |-------------------|---------|--------------------------------------------|
 * | id                | INTEGER | PK auto-increment                          |
 * | format            | TEXT    | [ExportFormat.name] — "PDF" or "CSV"       |
 * | year              | INTEGER | CRA tax year, e.g. 2026                    |
 * | vehicle_id        | INTEGER | FK to vehicles.id; nullable = all vehicles |
 * | generated_at      | INTEGER | Epoch millis of export completion          |
 * | file_uri          | TEXT    | file:// URI inside app cache dir           |
 * | file_size_bytes   | INTEGER | Size of the exported file in bytes         |
 *
 * @property id             Auto-generated primary key.
 * @property format         [ExportFormat.name] string ("PDF" or "CSV").
 * @property year           CRA tax year the export covers.
 * @property vehicleId      FK to the exported vehicle, or null for all-vehicle exports.
 * @property generatedAt    Epoch millis when the export completed.
 * @property fileUri        `file://` path to the cached export file.
 * @property fileSizeBytes  Size of the exported file in bytes.
 */
@Entity(
    tableName = "report_history",
    indices = [
        Index("year"),
        Index("vehicle_id"),
        Index("generated_at"),
    ],
)
data class ReportHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "format")
    val format: String,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "vehicle_id")
    val vehicleId: Int?,

    @ColumnInfo(name = "generated_at")
    val generatedAt: Long,

    @ColumnInfo(name = "file_uri")
    val fileUri: String,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
)
