package com.example.mileagetracker.domain.model

/**
 * Domain model representing a single vehicle expense logged by the user.
 *
 * All monetary fields are in **Canadian dollars (CAD)**.
 *
 * ## CRA business-use calculation
 * [businessUseAmount] is not persisted; it is computed by
 * [GetVehicleExpensesUseCase] by multiplying [amountCad] by the vehicle's
 * annual business-use percentage from [GetAnnualVehicleSummaryUseCase].
 *
 * @param id                     Auto-generated Row ID (0 = not yet persisted).
 * @param vehicleId              FK referencing the owning [VehicleEntity].
 * @param category               Categorisation of the expense (see [ExpenseCategory]).
 * @param amountCad              Total expense amount in CAD (must be > 0).
 * @param date                   Date the expense was incurred (epoch millis, start of day UTC).
 * @param vendorName             Optional vendor or payee name (e.g. "Shell", "Midas").
 * @param notes                  Optional free-form note.
 * @param receiptAttachmentUri   App-internal `file://` URI of a saved receipt image, or null.
 * @param gstHstAmount           GST/HST component of the expense, or null if not captured.
 * @param isBusinessUse          Whether this expense was incurred for business purposes;
 *                               used to gate 100 %-deductible categories (Parking, Tolls).
 * @param businessUseAmount      Computed deductible portion — NOT persisted; injected by use-case.
 * @param createdAt              Row-insert timestamp (epoch millis).
 */
data class VehicleExpense(
    val id: Int = 0,
    val vehicleId: Int,
    val category: ExpenseCategory,
    val amountCad: Double,
    val date: Long,
    val vendorName: String? = null,
    val notes: String? = null,
    val receiptAttachmentUri: String? = null,
    val gstHstAmount: Double? = null,
    val isBusinessUse: Boolean = true,
    val businessUseAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
)
