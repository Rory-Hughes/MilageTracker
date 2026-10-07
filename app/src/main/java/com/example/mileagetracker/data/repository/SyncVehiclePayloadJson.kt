package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import org.json.JSONObject

internal fun List<VehicleEntity>.toVehiclesPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toVehicleEntities(): List<VehicleEntity> =
    fromJsonArray { it.toVehicleEntity() }

internal fun List<UserClassificationRuleEntity>.toUserClassificationRulesPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toUserClassificationRuleEntities(): List<UserClassificationRuleEntity> =
    fromJsonArray { it.toUserClassificationRuleEntity() }

private fun VehicleEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("ownerUid", ownerUid)
    put("displayName", displayName)
    putNullable("make", make)
    putNullable("model", model)
    putNullable("year", year)
    put("ownershipType", ownershipType.name)
    putNullable("vehicleType", vehicleType)
    put("isActive", isActive)
    put("isDefault", isDefault)
    put("fiscalYear", fiscalYear)
    putNullable("openingOdometerKm", openingOdometerKm)
    putNullable("closingOdometerKm", closingOdometerKm)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toVehicleEntity() = VehicleEntity(
    id = getInt("id"),
    ownerUid = getString("ownerUid"),
    displayName = getString("displayName"),
    make = optStringOrNull("make"),
    model = optStringOrNull("model"),
    year = optIntOrNull("year"),
    ownershipType = VehicleOwnershipType.valueOf(getString("ownershipType")),
    vehicleType = optStringOrNull("vehicleType"),
    isActive = getBoolean("isActive"),
    isDefault = getBoolean("isDefault"),
    fiscalYear = getInt("fiscalYear"),
    openingOdometerKm = optFloatOrNull("openingOdometerKm"),
    closingOdometerKm = optFloatOrNull("closingOdometerKm"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun UserClassificationRuleEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("ownerUid", ownerUid)
    put("name", name)
    put("keyword", keyword)
    put("category", category)
    put("confidence", confidence.toDouble())
    put("priority", priority)
    put("isEnabled", isEnabled)
    put("createdAt", createdAt)
}

private fun JSONObject.toUserClassificationRuleEntity() = UserClassificationRuleEntity(
    id = getInt("id"),
    ownerUid = getString("ownerUid"),
    name = getString("name"),
    keyword = getString("keyword"),
    category = getString("category"),
    confidence = getDouble("confidence").toFloat(),
    priority = getInt("priority"),
    isEnabled = getBoolean("isEnabled"),
    createdAt = getLong("createdAt"),
)
