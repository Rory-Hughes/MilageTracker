package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import org.json.JSONObject

internal fun List<RoutePointEntity>.toRoutePointsPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toRoutePointEntities(): List<RoutePointEntity> =
    fromJsonArray { it.toRoutePointEntity() }

internal fun List<TripAuditEntity>.toTripAuditPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toTripAuditEntities(): List<TripAuditEntity> =
    fromJsonArray { it.toTripAuditEntity() }

private fun RoutePointEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("lat", lat)
    put("lng", lng)
    putNullable("altitudeMetres", altitudeMetres)
    putNullable("accuracyMetres", accuracyMetres?.toDouble())
    putNullable("speedKph", speedKph?.toDouble())
    put("timestampMs", timestampMs)
}

private fun JSONObject.toRoutePointEntity() = RoutePointEntity(
    id = getLong("id"),
    tripId = getLong("tripId"),
    lat = getDouble("lat"),
    lng = getDouble("lng"),
    altitudeMetres = optDoubleOrNull("altitudeMetres"),
    accuracyMetres = optFloatOrNull("accuracyMetres"),
    speedKph = optFloatOrNull("speedKph"),
    timestampMs = getLong("timestampMs"),
)

private fun TripAuditEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("actorUid", actorUid)
    put("eventType", eventType.name)
    putNullable("fieldName", fieldName)
    putNullable("oldValue", oldValue)
    putNullable("newValue", newValue)
    put("changedAt", changedAt)
}

private fun JSONObject.toTripAuditEntity() = TripAuditEntity(
    id = getInt("id"),
    tripId = getInt("tripId"),
    actorUid = getString("actorUid"),
    eventType = AuditEventType.valueOf(getString("eventType")),
    fieldName = optStringOrNull("fieldName"),
    oldValue = optStringOrNull("oldValue"),
    newValue = optStringOrNull("newValue"),
    changedAt = getLong("changedAt"),
)
