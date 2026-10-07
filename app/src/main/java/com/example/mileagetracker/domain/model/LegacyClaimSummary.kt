package com.example.mileagetracker.domain.model

data class LegacyClaimSummary(
    val driveCount: Int,
    val vehicleCount: Int,
    val ruleCount: Int = 0,
) {
    val hasClaimableData: Boolean
        get() = driveCount > 0 || vehicleCount > 0 || ruleCount > 0
}
