package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.LocalDataInventory
import com.example.mileagetracker.domain.model.PrivacyDashboardSnapshot
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.PrivacyDashboardUiState
import com.example.mileagetracker.ui.viewmodel.PrivacyDashboardViewModel

@Composable
fun PrivacyDashboardScreen(
    viewModel: PrivacyDashboardViewModel = hiltViewModel(),
) {
    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
        PrivacyDashboardUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is PrivacyDashboardUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = viewModel::refresh) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }

        is PrivacyDashboardUiState.Success -> {
            PrivacyDashboardContent(snapshot = state.snapshot)
        }
    }
}

@Composable
private fun PrivacyDashboardContent(snapshot: PrivacyDashboardSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CloudBackupSection(snapshot.cloudBackupEnabled)
        LocalDataSection(snapshot.localDataInventory)
        SyncStatusSection(snapshot)
        SignOutReadinessSection(snapshot)
        PrivacyDashboardFooter()
    }
}

@Composable
private fun CloudBackupSection(cloudBackupEnabled: Boolean) {
    PrivacyCard(title = stringResource(R.string.settings_section_privacy)) {
        Text(
            text = if (cloudBackupEnabled) {
                "Cloud backup is enabled for this account."
            } else {
                "Cloud backup is disabled. Local-only data will be cleared if you sign out."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocalDataSection(inventory: LocalDataInventory) {
    PrivacyCard(title = stringResource(R.string.privacy_dashboard_local_title)) {
        InventoryRow(label = "Trips", value = inventory.drives)
        InventoryRow(label = "Vehicles", value = inventory.vehicles)
        InventoryRow(label = "Classification rules", value = inventory.classificationRules)
        InventoryRow(label = "Trip attachments", value = inventory.tripAttachments)
        InventoryRow(label = "Route points", value = inventory.routePoints)
        InventoryRow(label = "Audit entries", value = inventory.tripAuditEntries)
        InventoryRow(label = "Vehicle expenses", value = inventory.vehicleExpenses)
        InventoryRow(label = "Odometer captures", value = inventory.odometerCaptures)
        InventoryRow(label = "Active tracking sessions", value = inventory.activeTrackingSessions)
        Spacer(modifier = Modifier.height(Spacing.sm))
        EvidenceSummary(inventory = inventory)
    }
}

@Composable
private fun SyncStatusSection(snapshot: PrivacyDashboardSnapshot) {
    PrivacyCard(title = stringResource(R.string.privacy_dashboard_sync_title)) {
        Text(
            text = if (snapshot.hasActiveTrip) {
                "An active trip is being recorded right now."
            } else {
                "No active trip is being recorded."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        InventoryRow(label = "Pending sync records", value = snapshot.pendingSyncCount)
        InventoryRow(
            label = "Unresolved sync conflicts",
            value = snapshot.unresolvedConflictCount,
        )
    }
}

@Composable
private fun SignOutReadinessSection(snapshot: PrivacyDashboardSnapshot) {
    PrivacyCard(title = stringResource(R.string.privacy_dashboard_sign_out_title)) {
        Text(
            text = snapshot.signOutReadiness.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
        )
        ReadinessNotes(
            notes = snapshot.signOutReadiness.blockingIssues,
            color = MaterialTheme.colorScheme.error,
        )
        ReadinessNotes(
            notes = snapshot.signOutReadiness.advisories,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadinessNotes(
    notes: List<String>,
    color: androidx.compose.ui.graphics.Color,
) {
    if (notes.isEmpty()) return
    Spacer(modifier = Modifier.height(Spacing.sm))
    notes.forEach { note ->
        Text(
            text = "- $note",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun PrivacyDashboardFooter() {
    Text(
        text = stringResource(R.string.privacy_dashboard_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
private fun PrivacyCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun InventoryRow(
    label: String,
    value: Int,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EvidenceSummary(inventory: LocalDataInventory) {
    Text(
        text =
            "Evidence files: ${inventory.totalEvidenceFiles} " +
                "(attachments ${inventory.attachmentFiles}, " +
                "odometer ${inventory.odometerPhotoFiles}, " +
                "receipts ${inventory.expenseReceiptFiles})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
