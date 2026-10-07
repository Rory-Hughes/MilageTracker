package com.example.mileagetracker.domain.usecase.account

import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.SignOutReadiness
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetSignOutReadinessUseCase @Inject constructor(
    private val currentUserProvider: CurrentUserProvider,
    private val activeTripStore: ActiveTripStore,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val getLocalDataInventoryUseCase: GetLocalDataInventoryUseCase,
) {
    suspend operator fun invoke(): SignOutReadiness {
        val uid = currentUserProvider.requireUid()
        return SignOutReadiness(
            cloudBackupEnabled = settingsRepository.getCloudUploadConsentVersion().first() ==
                CloudUploadConsent.CURRENT_VERSION,
            hasActiveTrip = activeTripStore.hasActiveTrip(),
            pendingSyncCount = syncRepository.pendingCount(),
            unresolvedConflictCount = syncRepository.unresolvedConflicts().size,
            localDataInventory = getLocalDataInventoryUseCase(uid),
        )
    }
}
