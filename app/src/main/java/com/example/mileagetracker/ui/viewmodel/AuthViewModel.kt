package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LegacyClaimSummary
import com.example.mileagetracker.domain.model.SignOutReadiness
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.usecase.account.DeleteAccountUseCase
import com.example.mileagetracker.domain.usecase.account.GetSignOutReadinessUseCase
import com.example.mileagetracker.domain.usecase.account.LegacyClaimUseCase
import com.example.mileagetracker.service.AutomaticTrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val currentUserProvider: CurrentUserProvider,
    private val syncRepository: SyncRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val legacyClaimUseCase: LegacyClaimUseCase,
    private val getSignOutReadinessUseCase: GetSignOutReadinessUseCase,
    private val automaticTrackingController: AutomaticTrackingController,
) : ViewModel() {

    // Check if the user is already logged in when the app starts
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    private val _signOutReadinessState = MutableStateFlow(SignOutReadinessUiState())
    val signOutReadinessState: StateFlow<SignOutReadinessUiState> = _signOutReadinessState

    init {
        if (auth.currentUser != null) {
            refreshSignOutReadiness()
        }
    }

    // All Firebase auth calls are handled uniformly through coroutines + Tasks.await()
    // (see kotlinx-coroutines-play-services) rather than mixing callback listeners
    // with the coroutine-based functions below.
    fun login(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                auth.signInWithEmailAndPassword(email, pass).await()
                if (auth.currentUser?.isEmailVerified == true) {
                    runCatching { syncRepository.restoreRemoteData() }
                    _isLoggedIn.value = true
                    refreshSignOutReadiness()
                    onSuccess()
                } else {
                    auth.currentUser?.sendEmailVerification()
                    auth.signOut()
                    _isLoggedIn.value = false
                    onError("Verify your email before signing in. A new verification email was sent.")
                }
            }.onFailure { error ->
                onError(error.message ?: "Login failed")
            }
        }
    }

    /**
     * Creates an account. Sign-up never logs the user in immediately — email
     * verification is required first — so the pending-verification outcome is
     * surfaced via [onPendingVerification] (an informational success), keeping
     * [onError] strictly for genuine failures.
     */
    fun signUp(
        email: String,
        pass: String,
        onPendingVerification: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                auth.createUserWithEmailAndPassword(email, pass).await()
                auth.currentUser?.sendEmailVerification()
                auth.signOut()
                _isLoggedIn.value = false
                onPendingVerification("Account created. Check your inbox to verify your email, then log in.")
            }.onFailure { error ->
                onError(error.message ?: "Sign up failed")
            }
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                auth.sendPasswordResetEmail(email).await()
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Unable to send password reset email")
            }
        }
    }

    fun signOut(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val uid = currentUserProvider.requireUid()
                val initialReadiness = loadSignOutReadiness()
                if (!initialReadiness.canAttemptSignOut) {
                    onError(initialReadiness.failureMessage ?: "Unable to sign out safely.")
                    return@launch
                }
                if (initialReadiness.cloudBackupEnabled) {
                    syncRepository.push()
                }
                val finalReadiness = loadSignOutReadiness()
                if (!finalReadiness.canFinishImmediately) {
                    onError(finalReadiness.failureMessage ?: "Unable to sync and sign out safely.")
                    return@launch
                }
                syncRepository.clearLocalCache(uid)
                automaticTrackingController.setEnabled(false)
                auth.signOut()
                _isLoggedIn.value = false
                _signOutReadinessState.value = SignOutReadinessUiState()
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Unable to sync and sign out safely.")
            }
        }
    }

    fun deleteAccount(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                deleteAccountUseCase(password)
                _isLoggedIn.value = false
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Account deletion failed. Sign in again and retry.")
            }
        }
    }

    fun refreshSignOutReadiness() {
        if (auth.currentUser == null) {
            _signOutReadinessState.value = SignOutReadinessUiState()
            return
        }
        viewModelScope.launch {
            _signOutReadinessState.value = _signOutReadinessState.value.copy(
                isLoading = true,
                loadError = null,
            )
            _signOutReadinessState.value = runCatching {
                SignOutReadinessUiState(readiness = loadSignOutReadiness())
            }.getOrElse { error ->
                SignOutReadinessUiState(loadError = error.message ?: "Unable to load sign-out status.")
            }
        }
    }

    fun previewLegacyClaim(onSuccess: (LegacyClaimSummary) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                onSuccess(legacyClaimUseCase.preview())
            }.onFailure { error ->
                onError(error.message ?: "Unable to check claimable legacy records.")
            }
        }
    }

    fun claimLegacyRecords(
        onSuccess: (LegacyClaimSummary) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                onSuccess(legacyClaimUseCase.claim())
            }.onFailure { error ->
                onError(error.message ?: "Unable to claim legacy records.")
            }
        }
    }

    // Also expose the current user's details for the Profile screen
    val currentUserEmail: String
        get() = currentUserProvider.currentUser()?.email ?: "No email"

    val currentUserDisplayName: String
        get() = currentUserProvider.currentUser()?.displayName ?: "Mileage Tracker User"

    // DateTimeFormatter is immutable and thread-safe, so a single cached instance
    // is reused for every access instead of allocating a formatter per call.
    private val createdAtFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.getDefault())

    val currentUserCreatedAt: String
        get() {
            val timestamp = currentUserProvider.currentUser()?.createdAtMillis ?: return "Unknown"
            return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(createdAtFormatter)
        }

    private suspend fun loadSignOutReadiness(): SignOutReadiness {
        val readiness = getSignOutReadinessUseCase()
        _signOutReadinessState.value = SignOutReadinessUiState(readiness = readiness)
        return readiness
    }
}

data class SignOutReadinessUiState(
    val isLoading: Boolean = false,
    val readiness: SignOutReadiness? = null,
    val loadError: String? = null,
)
