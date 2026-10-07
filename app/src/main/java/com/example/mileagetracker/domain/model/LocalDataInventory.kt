package com.example.mileagetracker.domain.model

data class LocalDataInventory(
    val drives: Int = 0,
    val vehicles: Int = 0,
    val classificationRules: Int = 0,
    val tripAttachments: Int = 0,
    val routePoints: Int = 0,
    val tripAuditEntries: Int = 0,
    val vehicleExpenses: Int = 0,
    val odometerCaptures: Int = 0,
    val activeTrackingSessions: Int = 0,
    val attachmentFiles: Int = 0,
    val odometerPhotoFiles: Int = 0,
    val expenseReceiptFiles: Int = 0,
) {
    val totalRows: Int
        get() = drives +
            vehicles +
            classificationRules +
            tripAttachments +
            routePoints +
            tripAuditEntries +
            vehicleExpenses +
            odometerCaptures +
            activeTrackingSessions

    val totalEvidenceFiles: Int
        get() = attachmentFiles + odometerPhotoFiles + expenseReceiptFiles

    val hasAnyLocalData: Boolean
        get() = totalRows > 0 || totalEvidenceFiles > 0
}
