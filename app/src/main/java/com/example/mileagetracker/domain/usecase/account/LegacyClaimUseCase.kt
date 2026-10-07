package com.example.mileagetracker.domain.usecase.account

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LegacyClaimSummary
import javax.inject.Inject

class LegacyClaimUseCase @Inject constructor(
    private val database: MileageDatabase,
    private val accountDataDao: AccountDataDao,
    private val currentUserProvider: CurrentUserProvider,
) {
    suspend fun preview(): LegacyClaimSummary = LegacyClaimSummary(
        driveCount = accountDataDao.countLegacyDrives(),
        vehicleCount = accountDataDao.countLegacyVehicles(),
        ruleCount = accountDataDao.countLegacyClassificationRules(),
    )

    suspend fun claim(uid: String = currentUserProvider.requireUid()): LegacyClaimSummary =
        database.withTransaction {
            LegacyClaimSummary(
                driveCount = accountDataDao.claimLegacyDrives(uid),
                vehicleCount = accountDataDao.claimLegacyVehicles(uid),
                ruleCount = accountDataDao.claimLegacyClassificationRules(uid),
            )
        }
}
