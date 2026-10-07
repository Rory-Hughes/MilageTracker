package com.example.mileagetracker.domain.usecase.account

import com.example.mileagetracker.data.local.LocalDataStorageInspector
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.domain.model.LocalDataInventory
import javax.inject.Inject

class GetLocalDataInventoryUseCase @Inject constructor(
    private val accountDataDao: AccountDataDao,
    private val localDataStorageInspector: LocalDataStorageInspector,
) {
    suspend operator fun invoke(uid: String): LocalDataInventory = LocalDataInventory(
        drives = accountDataDao.countOwnedDrives(uid),
        vehicles = accountDataDao.countOwnedVehicles(uid),
        classificationRules = accountDataDao.countOwnedClassificationRules(uid),
        tripAttachments = accountDataDao.countOwnedAttachments(uid),
        routePoints = accountDataDao.countOwnedRoutePoints(uid),
        tripAuditEntries = accountDataDao.countOwnedTripAuditEntries(uid),
        vehicleExpenses = accountDataDao.countOwnedVehicleExpenses(uid),
        odometerCaptures = accountDataDao.countOwnedOdometerCaptures(uid),
        activeTrackingSessions = accountDataDao.countOwnedActiveTrackingSessions(uid),
        attachmentFiles = localDataStorageInspector.countAttachmentFiles(),
        odometerPhotoFiles = localDataStorageInspector.countOdometerPhotoFiles(),
        expenseReceiptFiles = localDataStorageInspector.countExpenseReceiptFiles(),
    )
}
