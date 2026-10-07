package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single vehicle expense.
 *
 * ## Table: `vehicle_expenses`
 *
 * ### Foreign key
 * [vehicleId] references `vehicles(id)` with `CASCADE` delete so that
 * hard-deleting a vehicle also removes its expense history.
 *
 * ### Indexes
 * - `vehicle_id`        — supports FK integrity checks and per-vehicle list queries.
 * - `vehicle_id, date`  — supports the most common query (expenses for a vehicle sorted by date).
 * - `date`              — supports year-range aggregate queries in [VehicleExpenseDao.getTotalForVehicleAndYear].
 *
 * @param id                    Auto-generated PK.
 * @param vehicleId             FK to [VehicleEntity.id].
 * @param categoryKey           Storage key from [ExpenseCategory.key]; see [ExpenseCategory.fromKey].
 * @param amountCad             Total amount in CAD (> 0).
 * @param date                  Epoch millis — date the expense was incurred.
 * @param vendorName            Optional vendor / payee name.
 * @param notes                 Optional free-form note.
 * @param receiptAttachmentUri  App-internal `file://` URI of a receipt image, or null.
 * @param gstHstAmount          GST/HST component of the expense, or null.
 * @param isBusinessUse         Whether this was a business-use expense.
 * @param createdAt             Row-insert timestamp (epoch millis).
 */
@Entity(
    tableName = "vehicle_expenses",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicle_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("vehicle_id"),
        Index("vehicle_id", "date"),
        Index("date"),
    ],
)
data class VehicleExpenseEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "vehicle_id")
    val vehicleId: Int,

    /** Stored as [ExpenseCategory.key]. */
    @ColumnInfo(name = "category_key")
    val categoryKey: String,

    @ColumnInfo(name = "amount_cad")
    val amountCad: Double,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "vendor_name")
    val vendorName: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "receipt_attachment_uri")
    val receiptAttachmentUri: String? = null,

    @ColumnInfo(name = "gst_hst_amount")
    val gstHstAmount: Double? = null,

    @ColumnInfo(name = "is_business_use")
    val isBusinessUse: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
