package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.LegacyClaimSummary
import com.example.mileagetracker.domain.model.SignOutReadiness
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.AuthViewModel

@Composable
internal fun ProfileSignOutDialog(
    authViewModel: AuthViewModel,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    state: SignOutDialogState,
    onSigningOutChange: (Boolean) -> Unit,
    onActionMessage: (ProfileActionMessage?) -> Unit,
    onSignOut: () -> Unit,
) {
    if (!visible) return
    SignOutDialog(
        state = state,
        onDismiss = {
            if (!state.signingOut) {
                onVisibleChange(false)
            }
        },
        onConfirm = {
            onSigningOutChange(true)
            authViewModel.signOut(
                onSuccess = onSignOut,
                onError = { error ->
                    onSigningOutChange(false)
                    onVisibleChange(false)
                    onActionMessage(ProfileActionMessage(error, isError = true))
                    authViewModel.refreshSignOutReadiness()
                },
            )
        },
    )
}

@Composable
internal fun ProfileDeleteDialog(
    authViewModel: AuthViewModel,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onActionMessage: (ProfileActionMessage?) -> Unit,
    onSignOut: () -> Unit,
) {
    if (!visible) return
    DeleteAccountDialog(
        password = password,
        onPasswordChange = onPasswordChange,
        onDismiss = { onVisibleChange(false) },
        onConfirm = {
            authViewModel.deleteAccount(
                password = password,
                onSuccess = onSignOut,
                onError = { error ->
                    onVisibleChange(false)
                    onActionMessage(ProfileActionMessage(error, isError = true))
                },
            )
        },
    )
}

@Composable
internal fun ProfileLegacyClaimDialog(
    authViewModel: AuthViewModel,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    state: LegacyClaimUiState,
    onStateChange: (LegacyClaimUiState) -> Unit,
    onActionMessage: (ProfileActionMessage?) -> Unit,
) {
    if (!visible) return
    LegacyClaimDialog(
        state = state,
        onDismiss = {
            if (!state.claiming) {
                onVisibleChange(false)
            }
        },
        onConfirm = {
            onStateChange(state.copy(claiming = true))
            authViewModel.claimLegacyRecords(
                onSuccess = { claimed ->
                    onStateChange(
                        LegacyClaimUiState(
                            summary = LegacyClaimSummary(0, 0),
                            previewLoading = false,
                            claiming = false,
                        )
                    )
                    onVisibleChange(false)
                    onActionMessage(
                        ProfileActionMessage(
                            text = buildClaimSuccessMessage(claimed),
                            isError = false,
                        )
                    )
                    authViewModel.refreshSignOutReadiness()
                },
                onError = { error ->
                    onStateChange(state.copy(claiming = false))
                    onActionMessage(ProfileActionMessage(error, isError = true))
                },
            )
        },
    )
}

@Composable
private fun SignOutDialog(
    state: SignOutDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Out") },
        text = {
            when {
                state.isLoading && state.readiness == null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Checking sync and sign-out status...")
                    }
                }

                state.loadError != null && state.readiness == null -> {
                    Text(state.loadError)
                }

                state.readiness != null -> {
                    SignOutReadinessContent(state.readiness)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.readiness?.canAttemptSignOut == true && !state.signingOut,
            ) {
                Text(
                    text = state.confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.signingOut,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteAccountDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "This permanently deletes your cloud mileage records, evidence files, " +
                        "and local cache. Enter your password to continue."
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = password.isNotBlank(),
            ) {
                Text("Delete Account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LegacyClaimDialog(
    state: LegacyClaimUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Claim Legacy Records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Review these records before assigning them to your signed-in account:")
                Text("Trips: ${state.summary?.driveCount ?: 0}")
                Text("Vehicles: ${state.summary?.vehicleCount ?: 0}")
                Text("Rules: ${state.summary?.ruleCount ?: 0}")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.claiming,
            ) {
                Text(if (state.claiming) "Claiming..." else "Claim Records")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.claiming,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SignOutReadinessContent(readiness: SignOutReadiness) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(readiness.statusMessage)
        BlockingIssueList(readiness.blockingIssues)
        AdvisoryList(readiness.advisories)
        Text(
            text = buildLocalInventorySummary(readiness),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockingIssueList(issues: List<String>) {
    if (issues.isEmpty()) return
    Text(
        text = "Before signing out:",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    issues.forEach { issue ->
        Text("- $issue", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AdvisoryList(notes: List<String>) {
    notes.forEach { note ->
        Text(
            text = "- $note",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildClaimSuccessMessage(claimed: LegacyClaimSummary): String =
    "Claim complete: ${claimed.driveCount} trips, ${claimed.vehicleCount} vehicles, " +
        "and ${claimed.ruleCount} rules assigned to this account."

private fun buildLocalInventorySummary(readiness: SignOutReadiness): String =
    "Local rows: ${readiness.localDataInventory.totalRows} | " +
        "Local files: ${readiness.localDataInventory.totalEvidenceFiles}"
