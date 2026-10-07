package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.TripAttachmentEntity
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import org.json.JSONArray
import org.json.JSONObject

internal object SyncEntityTypes {
    const val DRIVES = "drives"
    const val ROUTE_POINTS = "routePoints"
    const val TRIP_AUDIT = "tripAudit"
    const val VEHICLES = "vehicles"
    const val VEHICLE_EXPENSES = "vehicleExpenses"
    const val ODOMETER_CAPTURES = "odometerCaptures"
    const val TRIP_ATTACHMENTS = "tripAttachments"
    const val USER_CLASSIFICATION_RULES = "userClassificationRules"
}

internal data class TripAttachmentSyncItem(
    val entity: TripAttachmentEntity,
    val remotePath: String?,
)

internal data class VehicleExpenseSyncItem(
    val entity: VehicleExpenseEntity,
    val remotePath: String?,
)

internal data class OdometerCaptureSyncItem(
    val entity: OdometerCaptureEntity,
    val remotePath: String?,
)

internal fun <T> List<T>.toJsonArrayString(transform: (T) -> JSONObject): String =
    JSONArray(map(transform)).toString()

internal fun <T> String.fromJsonArray(transform: (JSONObject) -> T): List<T> =
    JSONArray(this).let { array ->
        List(array.length()) { index -> transform(array.getJSONObject(index)) }
    }

internal fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else getString(key)

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else getInt(key)

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else getLong(key)

internal fun JSONObject.optFloatOrNull(key: String): Float? =
    if (isNull(key)) null else getDouble(key).toFloat()

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else getDouble(key)
