package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.ui.theme.Spacing

@Composable
internal fun VehicleCard(
    vehicle: VehicleEntity,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
    onDelete: () -> Unit,
    onRecordOdometer: () -> Unit,
) {
    val cardColors = if (vehicle.isActive) {
        CardDefaults.elevatedCardColors()
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            VehicleCardHeader(vehicle = vehicle)
            VehicleSummary(vehicle = vehicle)
            VehicleStatusLabel(vehicle = vehicle)
            if (vehicle.isActive) {
                ActiveVehicleActions(
                    vehicle = vehicle,
                    onRecordOdometer = onRecordOdometer,
                    onSetDefault = onSetDefault,
                    onEdit = onEdit,
                    onDeactivate = onDeactivate,
                )
            } else {
                InactiveVehicleActions(
                    onReactivate = onReactivate,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun VehicleCardHeader(vehicle: VehicleEntity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (vehicle.isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Text(
            text = vehicle.displayName,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (vehicle.isDefault) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.vehicle_status_default),
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun VehicleSummary(vehicle: VehicleEntity) {
    val summary = listOfNotNull(
        vehicle.make,
        vehicle.model,
        vehicle.year?.toString(),
    ).joinToString(" ")
    if (summary.isBlank()) return

    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp, start = 28.dp),
    )
}

@Composable
private fun VehicleStatusLabel(vehicle: VehicleEntity) {
    val statusLabel = when {
        !vehicle.isActive -> stringResource(R.string.vehicle_status_inactive)
        vehicle.isDefault -> stringResource(R.string.vehicle_status_default)
        else -> stringResource(R.string.vehicle_status_active)
    }
    Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelSmall,
        color = if (vehicle.isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        modifier = Modifier.padding(top = Spacing.xs, start = 28.dp),
    )
}

@Composable
private fun ActiveVehicleActions(
    vehicle: VehicleEntity,
    onRecordOdometer: () -> Unit,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onRecordOdometer) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = stringResource(R.string.vehicle_record_odometer),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        if (!vehicle.isDefault) {
            TextButton(onClick = onSetDefault) {
                Text(stringResource(R.string.vehicle_set_default))
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.action_edit),
            )
        }
        TextButton(onClick = onDeactivate) {
            Text(stringResource(R.string.vehicle_deactivate))
        }
    }
}

@Composable
private fun InactiveVehicleActions(
    onReactivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onReactivate) {
            Text(stringResource(R.string.vehicle_reactivate))
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.action_edit),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.vehicle_delete_confirm_title),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun EmptyVehicleState(
    modifier: Modifier,
    onAdd: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(R.string.vehicles_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.vehicles_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.vehicle_add),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}
