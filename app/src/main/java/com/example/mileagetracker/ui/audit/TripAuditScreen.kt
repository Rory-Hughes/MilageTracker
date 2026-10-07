package com.example.mileagetracker.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.ui.common.EmptyStateView
import com.example.mileagetracker.ui.common.ErrorStateView
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.ui.viewmodel.TripAuditUiState
import com.example.mileagetracker.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripAuditScreen(
    uiState: TripAuditUiState,
    onNavigateBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = when (uiState) {
                        is TripAuditUiState.Success ->
                            stringResource(R.string.audit_screen_title, uiState.tripId)
                        else ->
                            stringResource(R.string.audit_screen_title_generic)
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
        )

        when (uiState) {
            is TripAuditUiState.Loading -> AuditLoadingState()
            is TripAuditUiState.Error -> ErrorStateView(
                message = uiState.message,
                onRetry = onRetry,
            )
            is TripAuditUiState.Success -> {
                if (uiState.auditRows.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.audit_empty_title),
                        body = stringResource(R.string.audit_empty_body),
                    )
                } else {
                    AuditTimeline(auditRows = uiState.auditRows)
                }
            }
        }
    }
}

@Composable
private fun AuditLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuditTimeline(auditRows: List<TripAuditEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(
                text = pluralStringResource(
                    R.plurals.audit_timeline_subtitle,
                    auditRows.size,
                    auditRows.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        items(
            items = auditRows,
            key = { it.id },
        ) { row ->
            AuditEventRow(row = row)
        }
    }
}

private fun formatAuditTimestamp(epochMs: Long): String = DateFormats.uiDateTimeDot(epochMs)

@Composable
private fun AuditEventRow(row: TripAuditEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            AuditEventHeader(row = row)
            AuditActorLabel(row = row)
            AuditFieldDiff(row = row)
        }
    }
}

@Composable
private fun AuditEventHeader(row: TripAuditEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (row.actorUid == "SYSTEM") {
                    Icons.Default.SmartToy
                } else {
                    Icons.Default.Person
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = row.eventType.toDisplayLabel(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = formatAuditTimestamp(row.changedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuditActorLabel(row: TripAuditEntity) {
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = if (row.actorUid == "SYSTEM") {
            stringResource(R.string.audit_actor_system)
        } else {
            stringResource(R.string.audit_actor_user, row.actorUid)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AuditFieldDiff(row: TripAuditEntity) {
    val fieldName = row.fieldName ?: return

    Spacer(Modifier.height(Spacing.sm))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(Spacing.sm))

    Text(
        text = stringResource(R.string.audit_field_label, fieldName),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xs))

    val oldVal = row.oldValue
    val newVal = row.newValue
    if (oldVal == null && newVal == null) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (oldVal != null) {
            DiffLabel(
                label = stringResource(R.string.audit_diff_old, oldVal),
                isOld = true,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        if (newVal != null) {
            DiffLabel(
                label = stringResource(R.string.audit_diff_new, newVal),
                isOld = false,
            )
        }
    }
}

@Composable
private fun DiffLabel(label: String, isOld: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (isOld) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        fontWeight = FontWeight.Medium,
    )
}

// Exhaustive 1:1 enum→label map. High cyclomatic count is inherent to the
// branch-per-value form; keeping the `when` preserves compile-time
// exhaustiveness so a new AuditEventType can't silently miss a label.
@Suppress("CyclomaticComplexMethod")
private fun AuditEventType.toDisplayLabel(): String = when (this) {
    AuditEventType.DRIVE_CREATED -> "Trip created"
    AuditEventType.MANUAL_ENTRY -> "Manual entry"
    AuditEventType.TRIP_REPAIRED -> "Trip repaired"
    AuditEventType.CATEGORY_CHANGED -> "Category changed"
    AuditEventType.PURPOSE_UPDATED -> "Purpose updated"
    AuditEventType.DISTANCE_UPDATED -> "Distance updated"
    AuditEventType.LOCATION_UPDATED -> "Location updated"
    AuditEventType.VEHICLE_ASSIGNED -> "Vehicle assigned"
    AuditEventType.NOTE_UPDATED -> "Note updated"
    AuditEventType.ATTACHMENT_ADDED -> "Attachment added"
    AuditEventType.DUPLICATE_FLAGGED -> "Duplicate flagged"
    AuditEventType.TRIP_MERGED -> "Trips merged"
    AuditEventType.SUGGESTION_ACCEPTED -> "Suggestion accepted"
    AuditEventType.AUTO_CLASSIFIED -> "Auto-classified"
}
