package com.example.mileagetracker.domain.usecase.account

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.service.AutomaticTrackingController
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val activeTripStore: ActiveTripStore,
    private val syncRepository: SyncRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val automaticTrackingController: AutomaticTrackingController,
) {
    suspend operator fun invoke(password: String) {
        require(password.isNotBlank()) { "Enter your password to confirm account deletion." }
        check(!activeTripStore.hasActiveTrip()) {
            "Stop the active trip before deleting your account."
        }

        val user = auth.currentUser ?: error("No signed-in account.")
        val email = user.email ?: error("This account does not have an email address.")
        val uid = currentUserProvider.requireUid()
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()

        functions.getHttpsCallable("deleteAccountData").call().await()
        runCatching { syncRepository.clearLocalCache(uid) }
        automaticTrackingController.setEnabled(false)
        auth.signOut()
    }
}
