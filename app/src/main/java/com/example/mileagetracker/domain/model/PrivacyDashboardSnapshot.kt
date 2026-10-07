package com.example.mileagetracker.domain.model

data class PrivacyDashboardSnapshot(
    val signOutReadiness: SignOutReadiness,
) {
    val cloudBackupEnabled: Boolean
        get() = signOutReadiness.cloudBackupEnabled

    val localDataInventory: LocalDataInventory
        get() = signOutReadiness.localDataInventory

    val pendingSyncCount: Int
        get() = signOutReadiness.pendingSyncCount

    val unresolvedConflictCount: Int
        get() = signOutReadiness.unresolvedConflictCount

    val hasActiveTrip: Boolean
        get() = signOutReadiness.hasActiveTrip
}
