package com.example.mileagetracker.domain.usecase.account

import com.example.mileagetracker.domain.model.PrivacyDashboardSnapshot
import javax.inject.Inject

class GetPrivacyDashboardSnapshotUseCase @Inject constructor(
    private val getSignOutReadinessUseCase: GetSignOutReadinessUseCase,
) {
    suspend operator fun invoke(): PrivacyDashboardSnapshot =
        PrivacyDashboardSnapshot(signOutReadiness = getSignOutReadinessUseCase())
}
