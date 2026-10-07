package com.example.mileagetracker.domain.model

data class SignOutReadiness(
    val cloudBackupEnabled: Boolean,
    val hasActiveTrip: Boolean,
    val pendingSyncCount: Int,
    val unresolvedConflictCount: Int,
    val localDataInventory: LocalDataInventory,
) {
    val hasLocalData: Boolean
        get() = localDataInventory.hasAnyLocalData

    val requiresSyncBeforeSignOut: Boolean
        get() = cloudBackupEnabled && pendingSyncCount > 0

    val canAttemptSignOut: Boolean
        get() = blockingIssues.isEmpty()

    val canFinishImmediately: Boolean
        get() = canAttemptSignOut && !requiresSyncBeforeSignOut

    val blockingIssues: List<String>
        get() = buildList {
            if (hasActiveTrip) {
                add("Stop the active trip before signing out.")
            }
            if (unresolvedConflictCount > 0) {
                add("Resolve sync conflicts before signing out.")
            }
            if (!cloudBackupEnabled && hasLocalData) {
                add(
                    "Turn on cloud backup or export your records before signing out. " +
                        "Signing out clears the local copy on this device."
                )
            }
        }

    val advisories: List<String>
        get() = buildList {
            if (requiresSyncBeforeSignOut) {
                add(
                    "MileageTracker will upload $pendingSyncCount pending sync " +
                        if (pendingSyncCount == 1) "record before signing out." else "records before signing out."
                )
            }
            if (cloudBackupEnabled && hasLocalData) {
                add("After sign-out, the synced local cache on this device will be cleared.")
            }
            if (!hasLocalData) {
                add("No local mileage records are stored on this device.")
            }
        }

    val statusMessage: String
        get() = when {
            hasActiveTrip -> "An active trip is still being recorded."
            unresolvedConflictCount > 0 -> "Sync conflicts need review before you sign out."
            !cloudBackupEnabled && hasLocalData ->
                "Cloud backup is off, so signing out would remove the only local copy on this device."
            requiresSyncBeforeSignOut ->
                "Your data is backed up, but MileageTracker still needs to finish syncing before sign-out."
            hasLocalData ->
                "Your data is backed up and safe to clear from this device during sign-out."
            else -> "This account can sign out without clearing any local mileage records."
        }

    val failureMessage: String?
        get() = when {
            blockingIssues.isNotEmpty() -> blockingIssues.first()
            requiresSyncBeforeSignOut ->
                "Some mileage records are still waiting to upload. Retry sign-out when you are online."
            else -> null
        }
}
