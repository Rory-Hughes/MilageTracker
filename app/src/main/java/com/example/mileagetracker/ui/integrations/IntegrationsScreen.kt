package com.example.mileagetracker.ui.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mileagetracker.domain.model.IntegrationProvider
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.util.DateFormats
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    onNavigateToDetail: (IntegrationProvider) -> Unit,
    viewModel: IntegrationsViewModel = hiltViewModel()
) {
    val integrations by viewModel.integrations.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncState) {
        when (syncState) {
            is IntegrationsViewModel.SyncState.Connected ->
                snackbarHostState.showSnackbar(
                    "Connected to ${(syncState as IntegrationsViewModel.SyncState.Connected).provider.displayName()}"
                )
            is IntegrationsViewModel.SyncState.Disconnected ->
                snackbarHostState.showSnackbar(
                    "Disconnected from ${
                        (syncState as IntegrationsViewModel.SyncState.Disconnected)
                            .provider
                            .displayName()
                    }"
                )
            else -> Unit
        }
        viewModel.resetSyncState()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Integrations") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.sm
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(IntegrationProvider.entries) { provider ->
                val config = integrations.firstOrNull { it.provider == provider }
                IntegrationCard(
                    provider = provider,
                    isConnected = config != null,
                    lastSyncAt = config?.lastSyncAt,
                    onClick = { onNavigateToDetail(provider) }
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    }
}

@Composable
private fun IntegrationCard(
    provider: IntegrationProvider,
    isConnected: Boolean,
    lastSyncAt: Long?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.thin)
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.lg)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isConnected) "Connected" else "Not connected",
                tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) {
                        if (lastSyncAt != null) {
                            "Last synced ${DateFormats.uiDateTime(lastSyncAt, Locale.CANADA)}"
                        } else {
                            "Connected — never synced"
                        }
                    } else {
                        provider.description()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isConnected) "Manage" else "Connect",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun IntegrationProvider.displayName(): String = when (this) {
    IntegrationProvider.QUICKBOOKS -> "QuickBooks Online"
    IntegrationProvider.XERO -> "Xero"
    IntegrationProvider.PAYROLL_CSV -> "Payroll CSV (Wagepoint)"
}

private fun IntegrationProvider.description(): String = when (this) {
    IntegrationProvider.QUICKBOOKS -> "Export approved mileage & expenses to QBO"
    IntegrationProvider.XERO -> "Push expense claims directly to Xero"
    IntegrationProvider.PAYROLL_CSV -> "Download a Wagepoint-compatible CSV"
}
