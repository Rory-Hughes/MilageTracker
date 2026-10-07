package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import org.json.JSONObject

internal fun List<VehicleExpenseEntity>.toVehicleExpensesPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toVehicleExpenseEntities(): List<VehicleExpenseEntity> =
    fromJsonArray { it.toVehicleExpenseEntity() }

internal fun List<VehicleExpenseSyncItem>.toVehicleExpenseSyncPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toVehicleExpenseSyncItems(): List<VehicleExpenseSyncItem> =
    fromJsonArray { it.toVehicleExpenseSyncItem() }

private fun VehicleExpenseEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("vehicleId", vehicleId)
    put("categoryKey", categoryKey)
    put("amountCad", amountCad)
    put("date", date)
    putNullable("vendorName", vendorName)
    putNullable("notes", notes)
    putNullable("receiptAttachmentUri", receiptAttachmentUri)
    putNullable("gstHstAmount", gstHstAmount)
    put("isBusinessUse", isBusinessUse)
    put("createdAt", createdAt)
}

private fun JSONObject.toVehicleExpenseEntity() = VehicleExpenseEntity(
    id = getInt("id"),
    vehicleId = getInt("vehicleId"),
    categoryKey = getString("categoryKey"),
    amountCad = getDouble("amountCad"),
    date = getLong("date"),
    vendorName = optStringOrNull("vendorName"),
    notes = optStringOrNull("notes"),
    receiptAttachmentUri = optStringOrNull("receiptAttachmentUri"),
    gstHstAmount = optDoubleOrNull("gstHstAmount"),
    isBusinessUse = getBoolean("isBusinessUse"),
    createdAt = getLong("createdAt"),
)

private fun VehicleExpenseSyncItem.toJson() = JSONObject().apply {
    put("entity", entity.toJson())
    putNullable("remotePath", remotePath)
}

private fun JSONObject.toVehicleExpenseSyncItem() = VehicleExpenseSyncItem(
    entity = getJSONObject("entity").toVehicleExpenseEntity(),
    remotePath = optStringOrNull("remotePath"),
)
