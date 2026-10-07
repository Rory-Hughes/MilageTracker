package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.domain.model.LegacyClaimSummary
import com.example.mileagetracker.domain.model.SignOutReadiness
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.AuthViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLegacyClaimDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<ProfileActionMessage?>(null) }
    var legacyState by remember { mutableStateOf(LegacyClaimUiState()) }
    var signingOut by remember { mutableStateOf(false) }
    val signOutReadinessState by authViewModel.signOutReadinessState.collectAsStateWithLifecycle()

    ProfileEffects(
        authViewModel = authViewModel,
        showSignOutDialog = showSignOutDialog,
        onLegacyPreviewSuccess = { summary ->
            legacyState = legacyState.copy(summary = summary, previewLoading = false)
        },
        onLegacyPreviewError = { error ->
            actionMessage = ProfileActionMessage(error, isError = true)
            legacyState = legacyState.copy(previewLoading = false)
        },
    )

    ProfileDialogs(
        authViewModel = authViewModel,
        showSignOutDialog = showSignOutDialog,
        onShowSignOutDialogChange = { showSignOutDialog = it },
        signOutDialogState = SignOutDialogState(
            readiness = signOutReadinessState.readiness,
            isLoading = signOutReadinessState.isLoading,
            loadError = signOutReadinessState.loadError,
            signingOut = signingOut,
        ),
        onSigningOutChange = { signingOut = it },
        showDeleteDialog = showDeleteDialog,
        onShowDeleteDialogChange = { showDeleteDialog = it },
        deletePassword = deletePassword,
        onDeletePasswordChange = { deletePassword = it },
        showLegacyClaimDialog = showLegacyClaimDialog,
        onShowLegacyClaimDialogChange = { showLegacyClaimDialog = it },
        legacyState = legacyState,
        onLegacyStateChange = { legacyState = it },
        onActionMessage = { actionMessage = it },
        onSignOut = onSignOut,
    )

    ProfileContent(
        displayName = authViewModel.currentUserDisplayName,
        email = authViewModel.currentUserEmail,
        createdAt = authViewModel.currentUserCreatedAt,
        legacyState = legacyState,
        actionMessage = actionMessage,
        onClaimLegacyClick = { showLegacyClaimDialog = true },
        onSignOutClick = {
            signingOut = false
            showSignOutDialog = true
        },
        onDeleteAccountClick = { showDeleteDialog = true },
    )
}

@Composable
private fun ProfileEffects(
    authViewModel: AuthViewModel,
    showSignOutDialog: Boolean,
    onLegacyPreviewSuccess: (LegacyClaimSummary) -> Unit,
    onLegacyPreviewError: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        authViewModel.refreshSignOutReadiness()
        authViewModel.previewLegacyClaim(
            onSuccess = onLegacyPreviewSuccess,
            onError = onLegacyPreviewError,
        )
    }

    LaunchedEffect(showSignOutDialog) {
        if (showSignOutDialog) {
            authViewModel.refreshSignOutReadiness()
        }
    }
}

@Composable
private fun ProfileDialogs(
    authViewModel: AuthViewModel,
    showSignOutDialog: Boolean,
    onShowSignOutDialogChange: (Boolean) -> Unit,
    signOutDialogState: SignOutDialogState,
    onSigningOutChange: (Boolean) -> Unit,
    showDeleteDialog: Boolean,
    onShowDeleteDialogChange: (Boolean) -> Unit,
    deletePassword: String,
    onDeletePasswordChange: (String) -> Unit,
    showLegacyClaimDialog: Boolean,
    onShowLegacyClaimDialogChange: (Boolean) -> Unit,
    legacyState: LegacyClaimUiState,
    onLegacyStateChange: (LegacyClaimUiState) -> Unit,
    onActionMessage: (ProfileActionMessage?) -> Unit,
    onSignOut: () -> Unit,
) {
    ProfileSignOutDialog(
        authViewModel = authViewModel,
        visible = showSignOutDialog,
        onVisibleChange = onShowSignOutDialogChange,
        state = signOutDialogState,
        onSigningOutChange = onSigningOutChange,
        onActionMessage = onActionMessage,
        onSignOut = onSignOut,
    )
    ProfileDeleteDialog(
        authViewModel = authViewModel,
        visible = showDeleteDialog,
        onVisibleChange = onShowDeleteDialogChange,
        password = deletePassword,
        onPasswordChange = onDeletePasswordChange,
        onActionMessage = onActionMessage,
        onSignOut = onSignOut,
    )
    ProfileLegacyClaimDialog(
        authViewModel = authViewModel,
        visible = showLegacyClaimDialog,
        onVisibleChange = onShowLegacyClaimDialogChange,
        state = legacyState,
        onStateChange = onLegacyStateChange,
        onActionMessage = onActionMessage,
    )
}

@Composable
private fun ProfileContent(
    displayName: String,
    email: String,
    createdAt: String,
    legacyState: LegacyClaimUiState,
    actionMessage: ProfileActionMessage?,
    onClaimLegacyClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileHeader(displayName = displayName)
        Spacer(modifier = Modifier.height(Spacing.xxl))
        ProfileInfoCard(email = email, createdAt = createdAt)
        Spacer(modifier = Modifier.height(Spacing.lg))
        LegacyClaimCard(
            state = legacyState,
            onClaimClick = onClaimLegacyClick,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        ActionMessageText(actionMessage)
        SignOutButton(onClick = onSignOutClick)
        Spacer(modifier = Modifier.height(Spacing.md))
        DeleteAccountButton(onClick = onDeleteAccountClick)
    }
}

internal data class ProfileActionMessage(
    val text: String,
    val isError: Boolean,
)

internal data class LegacyClaimUiState(
    val summary: LegacyClaimSummary? = null,
    val previewLoading: Boolean = true,
    val claiming: Boolean = false,
)

internal data class SignOutDialogState(
    val readiness: SignOutReadiness?,
    val isLoading: Boolean,
    val loadError: String?,
    val signingOut: Boolean,
) {
    val confirmLabel: String
        get() = when {
            signingOut -> "Signing Out..."
            readiness?.requiresSyncBeforeSignOut == true -> "Sync and Sign Out"
            else -> "Sign Out"
        }
}
