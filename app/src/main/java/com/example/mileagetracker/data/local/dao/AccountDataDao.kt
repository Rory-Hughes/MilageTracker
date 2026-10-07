package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

interface AccountDataInventoryDao {
    @Query("SELECT COUNT(*) FROM drives WHERE driver_uid = :uid")
    suspend fun countOwnedDrives(uid: String): Int

    @Query("SELECT COUNT(*) FROM vehicles WHERE owner_uid = :uid")
    suspend fun countOwnedVehicles(uid: String): Int

    @Query("SELECT COUNT(*) FROM user_classification_rules WHERE owner_uid = :uid")
    suspend fun countOwnedClassificationRules(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM trip_attachments " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun countOwnedAttachments(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM route_points " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun countOwnedRoutePoints(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM trip_audit " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun countOwnedTripAuditEntries(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM vehicle_expenses " +
            "WHERE vehicle_id IN (SELECT id FROM vehicles WHERE owner_uid = :uid)"
    )
    suspend fun countOwnedVehicleExpenses(uid: String): Int

    @Query(
        "SELECT COUNT(*) FROM odometer_captures " +
            "WHERE vehicle_id IN (SELECT id FROM vehicles WHERE owner_uid = :uid)"
    )
    suspend fun countOwnedOdometerCaptures(uid: String): Int

    @Query("SELECT COUNT(*) FROM active_tracking_sessions WHERE owner_uid = :uid")
    suspend fun countOwnedActiveTrackingSessions(uid: String): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM drives WHERE driver_uid = :uid) +
            (SELECT COUNT(*) FROM vehicles WHERE owner_uid = :uid) +
            (SELECT COUNT(*) FROM user_classification_rules WHERE owner_uid = :uid)
        """
    )
    suspend fun countOwnedRootRecords(uid: String): Int
}

interface AccountLegacyClaimDao {
    @Query(
        "SELECT COUNT(*) FROM drives " +
            "WHERE driver_uid IS NULL OR driver_uid IN ('local', 'SYSTEM', 'LEGACY_USER')"
    )
    suspend fun countLegacyDrives(): Int

    @Query("SELECT COUNT(*) FROM vehicles WHERE owner_uid IN ('local', 'SYSTEM', 'LEGACY_USER')")
    suspend fun countLegacyVehicles(): Int

    @Query(
        "SELECT COUNT(*) FROM user_classification_rules " +
            "WHERE owner_uid IN ('local', 'SYSTEM', 'LEGACY_USER')"
    )
    suspend fun countLegacyClassificationRules(): Int

    @Query(
        "UPDATE drives SET driver_uid = :uid " +
            "WHERE driver_uid IS NULL " +
            "OR driver_uid IN ('local', 'SYSTEM', 'LEGACY_USER')"
    )
    suspend fun claimLegacyDrives(uid: String): Int

    @Query(
        "UPDATE vehicles SET owner_uid = :uid " +
            "WHERE owner_uid IN ('local', 'SYSTEM', 'LEGACY_USER')"
    )
    suspend fun claimLegacyVehicles(uid: String): Int

    @Query(
        "UPDATE user_classification_rules SET owner_uid = :uid " +
            "WHERE owner_uid IN ('local', 'SYSTEM', 'LEGACY_USER')"
    )
    suspend fun claimLegacyClassificationRules(uid: String): Int
}

interface AccountOwnedDataDeletionDao {
    @Query(
        "DELETE FROM trip_attachments " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun deleteAttachments(uid: String)

    @Query(
        "DELETE FROM route_points " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun deleteRoutePoints(uid: String)

    @Query(
        "DELETE FROM trip_audit " +
            "WHERE trip_id IN (SELECT id FROM drives WHERE driver_uid = :uid)"
    )
    suspend fun deleteTripAudit(uid: String)

    @Query(
        "DELETE FROM vehicle_expenses " +
            "WHERE vehicle_id IN (SELECT id FROM vehicles WHERE owner_uid = :uid)"
    )
    suspend fun deleteExpenses(uid: String)

    @Query(
        "DELETE FROM odometer_captures " +
            "WHERE vehicle_id IN (SELECT id FROM vehicles WHERE owner_uid = :uid)"
    )
    suspend fun deleteOdometerCaptures(uid: String)

    @Query("DELETE FROM drives WHERE driver_uid = :uid")
    suspend fun deleteDrives(uid: String)

    @Query("DELETE FROM vehicles WHERE owner_uid = :uid")
    suspend fun deleteVehicles(uid: String)

    @Query("DELETE FROM active_tracking_sessions WHERE owner_uid = :uid")
    suspend fun deleteActiveTrackingSession(uid: String)

    @Query("DELETE FROM user_classification_rules WHERE owner_uid = :uid")
    suspend fun deleteClassificationRules(uid: String)
}

@Dao
interface AccountDataDao :
    AccountDataInventoryDao,
    AccountLegacyClaimDao,
    AccountOwnedDataDeletionDao {

    @Transaction
    suspend fun clearOwnedData(uid: String) {
        deleteClassificationRules(uid)
        deleteAttachments(uid)
        deleteRoutePoints(uid)
        deleteTripAudit(uid)
        deleteExpenses(uid)
        deleteOdometerCaptures(uid)
        deleteDrives(uid)
        deleteVehicles(uid)
        deleteActiveTrackingSession(uid)
    }
}
