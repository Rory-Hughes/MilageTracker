package com.example.mileagetracker.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.ui.policy.PolicyPickerDropdown
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke

/**
 * Screen that allows an admin to invite a new driver or manager to the team.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverInviteScreen(
    onNavigateUp: () -> Unit,
    viewModel: DriverInviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    DriverInviteStatusEffect(
        statusMessage = uiState.statusMessage,
        snackbarHostState = snackbarHostState,
        onStatusConsumed = viewModel::clearStatus,
    )

    Scaffold(
        topBar = {
            DriverInviteTopBar(onNavigateUp = onNavigateUp)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            DriverInviteEmailField(
                email = uiState.email,
                emailError = uiState.emailError,
                onEmailChange = viewModel::onEmailChange,
            )
            DriverInviteRoleSelector(
                selectedRole = uiState.selectedRole,
                onRoleChange = viewModel::onRoleChange,
            )
            PolicyPickerDropdown(
                policies = policies,
                selectedId = uiState.selectedPolicyId,
                onPolicySelected = { viewModel.onPolicyChange(it?.id) },
                label = "Assign Policy Template (optional)",
            )
            Spacer(Modifier.weight(1f))
            DriverInviteSubmitButton(
                isLoading = uiState.isLoading,
                onSendInvite = viewModel::sendInvite,
            )
        }
    }
}

@Composable
private fun DriverInviteStatusEffect(
    statusMessage: String?,
    snackbarHostState: SnackbarHostState,
    onStatusConsumed: () -> Unit,
) {
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            onStatusConsumed()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverInviteTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text("Invite Team Member") },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate up",
                )
            }
        },
    )
}

@Composable
private fun DriverInviteEmailField(
    email: String,
    emailError: Boolean,
    onEmailChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address *") },
        isError = emailError,
        supportingText = if (emailError) {
            { Text("Enter a valid email address.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DriverInviteRoleSelector(
    selectedRole: TeamRole,
    onRoleChange: (TeamRole) -> Unit,
) {
    Text("Role", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TeamRole.entries.forEach { role ->
            FilterChip(
                selected = selectedRole == role,
                onClick = { onRoleChange(role) },
                label = {
                    Text(role.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                },
            )
        }
    }
}

@Composable
private fun DriverInviteSubmitButton(
    isLoading: Boolean,
    onSendInvite: () -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onSendInvite,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = Stroke.thin,
            )
        } else {
            Text("Send Invite")
        }
    }
}
