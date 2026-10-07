package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DetectionSource
import org.json.JSONObject

internal fun List<DriveEntity>.toDrivesPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toDriveEntities(): List<DriveEntity> =
    fromJsonArray { it.toDriveEntity() }

private fun DriveEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("startedAt", startedAt)
    putNullable("endedAt", endedAt)
    putNullable("dateString", dateString)
    putNullable("timeString", timeString)
    putNullable("timestamp", timestamp)
    put("startLocation", startLocation)
    put("endLocation", endLocation)
    put("distanceKm", distanceKm.toDouble())
    putNullable("vehicleId", vehicleId)
    putNullable("driverUid", driverUid)
    put("tripCategory", tripCategory.name)
    putNullable("purposeCategory", purposeCategory)
    put("classificationStatus", classificationStatus.name)
    putNullable("purposeText", purposeText)
    putNullable("classificationConfidence", classificationConfidence?.toDouble())
    putNullable("routePolyline", routePolyline)
    put("appliedRateSnapshot", appliedRateSnapshot.toDouble())
    putNullable("appliedRate", appliedRate?.toDouble())
    putNullable("reimbursementAmountSnapshot", reimbursementAmountSnapshot?.toDouble())
    putNullable("notes", notes)
    put("trackingSource", trackingSource.name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    putNullable("deletedAt", deletedAt)
    putNullable("trackingMetadata", trackingMetadata?.toJson())
}

private fun JSONObject.toDriveEntity() = DriveEntity(
    id = getInt("id"),
    startedAt = getLong("startedAt"),
    endedAt = optLongOrNull("endedAt"),
    dateString = optStringOrNull("dateString"),
    timeString = optStringOrNull("timeString"),
    timestamp = optLongOrNull("timestamp"),
    startLocation = getString("startLocation"),
    endLocation = getString("endLocation"),
    distanceKm = getDouble("distanceKm").toFloat(),
    vehicleId = optIntOrNull("vehicleId"),
    driverUid = optStringOrNull("driverUid"),
    tripCategory = TripCategory.valueOf(getString("tripCategory")),
    purposeCategory = optStringOrNull("purposeCategory"),
    classificationStatus = ClassificationStatus.valueOf(getString("classificationStatus")),
    purposeText = optStringOrNull("purposeText"),
    classificationConfidence = optFloatOrNull("classificationConfidence"),
    routePolyline = optStringOrNull("routePolyline"),
    appliedRateSnapshot = getDouble("appliedRateSnapshot").toFloat(),
    appliedRate = optFloatOrNull("appliedRate"),
    reimbursementAmountSnapshot = optFloatOrNull("reimbursementAmountSnapshot"),
    notes = optStringOrNull("notes"),
    trackingSource = TrackingSource.valueOf(getString("trackingSource")),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
    deletedAt = optLongOrNull("deletedAt"),
    trackingMetadata = optJSONObject("trackingMetadata")?.toTrackingMetadataEmbedded(),
)

private fun TrackingMetadataEmbedded.toJson() = JSONObject().apply {
    putNullable("detectionSource", detectionSource?.name)
    putNullable("confidenceScore", confidenceScore?.toDouble())
    putNullable("batteryLevelAtStart", batteryLevelAtStart)
    putNullable("batteryLevelAtEnd", batteryLevelAtEnd)
    put("wasBatterySaverActive", wasBatterySaverActive)
}

private fun JSONObject.toTrackingMetadataEmbedded() = TrackingMetadataEmbedded(
    detectionSource = optStringOrNull("detectionSource")?.let(DetectionSource::valueOf),
    confidenceScore = optFloatOrNull("confidenceScore"),
    batteryLevelAtStart = optIntOrNull("batteryLevelAtStart"),
    batteryLevelAtEnd = optIntOrNull("batteryLevelAtEnd"),
    wasBatterySaverActive = optBoolean("wasBatterySaverActive", false),
)
