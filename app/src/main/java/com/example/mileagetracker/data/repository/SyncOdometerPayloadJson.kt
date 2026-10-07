package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import org.json.JSONObject

internal fun List<OdometerCaptureEntity>.toOdometerCapturesPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toOdometerCaptureEntities(): List<OdometerCaptureEntity> =
    fromJsonArray { it.toOdometerCaptureEntity() }

internal fun List<OdometerCaptureSyncItem>.toOdometerCaptureSyncPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toOdometerCaptureSyncItems(): List<OdometerCaptureSyncItem> =
    fromJsonArray { it.toOdometerCaptureSyncItem() }

private fun OdometerCaptureEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("vehicleId", vehicleId)
    put("captureDate", captureDate)
    put("readingKm", readingKm)
    put("taxYear", taxYear)
    putNullable("photoUri", photoUri)
    put("captureType", captureType)
    put("boundaryType", boundaryType)
    putNullable("notes", notes)
    put("createdAt", createdAt)
}

private fun JSONObject.toOdometerCaptureEntity() = OdometerCaptureEntity(
    id = getInt("id"),
    vehicleId = getInt("vehicleId"),
    captureDate = getLong("captureDate"),
    readingKm = getInt("readingKm"),
    taxYear = optIntOrNull("taxYear") ?: 0,
    photoUri = optStringOrNull("photoUri"),
    captureType = getString("captureType"),
    boundaryType = optStringOrNull("boundaryType") ?: "OTHER",
    notes = optStringOrNull("notes"),
    createdAt = getLong("createdAt"),
)

private fun OdometerCaptureSyncItem.toJson() = JSONObject().apply {
    put("entity", entity.toJson())
    putNullable("remotePath", remotePath)
}

private fun JSONObject.toOdometerCaptureSyncItem() = OdometerCaptureSyncItem(
    entity = getJSONObject("entity").toOdometerCaptureEntity(),
    remotePath = optStringOrNull("remotePath"),
)
