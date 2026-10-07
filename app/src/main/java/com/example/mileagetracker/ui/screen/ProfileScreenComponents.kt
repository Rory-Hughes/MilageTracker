package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.domain.model.LegacyClaimSummary
import com.example.mileagetracker.ui.theme.Spacing

@Composable
internal fun ProfileHeader(displayName: String) {
    Spacer(modifier = Modifier.height(Spacing.xl))
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = "Profile Avatar",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(modifier = Modifier.height(Spacing.lg))
    Text(
        text = displayName,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun ProfileInfoCard(email: String, createdAt: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            ProfileInfoRow(
                icon = Icons.Filled.Email,
                label = "Email",
                value = email,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
            ProfileInfoRow(
                icon = Icons.Filled.CalendarToday,
                label = "Member Since",
                value = createdAt,
            )
        }
    }
}

@Composable
internal fun LegacyClaimCard(
    state: LegacyClaimUiState,
    onClaimClick: () -> Unit,
) {
    when {
        state.previewLoading -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        state.summary?.hasClaimableData == true -> {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Legacy records need review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatLegacySummary(state.summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onClaimClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                        Text("Review and Claim")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionMessageText(actionMessage: ProfileActionMessage?) {
    actionMessage?.let { message ->
        Text(
            text = message.text,
            color = if (message.isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
internal fun SignOutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text("Sign Out", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DeleteAccountButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            Icons.Filled.DeleteForever,
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text("Delete Account")
    }
}

@Composable
internal fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.lg))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

internal fun formatLegacySummary(summary: LegacyClaimSummary): String =
    "Trips: ${summary.driveCount} - Vehicles: ${summary.vehicleCount} - Rules: ${summary.ruleCount}"
