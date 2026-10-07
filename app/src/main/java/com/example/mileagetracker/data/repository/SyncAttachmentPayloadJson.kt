package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import org.json.JSONObject

internal fun List<TripAttachmentSyncItem>.toTripAttachmentsPayloadJson(): String =
    toJsonArrayString { it.toJson() }

internal fun String.toTripAttachmentSyncItems(): List<TripAttachmentSyncItem> =
    fromJsonArray { it.toTripAttachmentSyncItem() }

private fun TripAttachmentSyncItem.toJson() = JSONObject().apply {
    put("entity", entity.toJson())
    putNullable("remotePath", remotePath)
}

private fun JSONObject.toTripAttachmentSyncItem() = TripAttachmentSyncItem(
    entity = getJSONObject("entity").toTripAttachmentEntity(),
    remotePath = optStringOrNull("remotePath"),
)

private fun TripAttachmentEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("uri", uri)
    put("mimeType", mimeType)
    put("fileName", fileName)
    put("createdAt", createdAt)
}

private fun JSONObject.toTripAttachmentEntity() = TripAttachmentEntity(
    id = getInt("id"),
    tripId = getInt("tripId"),
    uri = getString("uri"),
    mimeType = getString("mimeType"),
    fileName = getString("fileName"),
    createdAt = getLong("createdAt"),
)
