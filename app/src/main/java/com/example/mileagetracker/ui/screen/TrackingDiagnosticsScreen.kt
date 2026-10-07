package com.example.mileagetracker.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.TrackingDiagnosticsViewModel

@Composable
fun TrackingDiagnosticsScreen(
    viewModel: TrackingDiagnosticsViewModel = hiltViewModel(),
) {
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    TrackingDiagnosticsEffects(
        viewModel = viewModel,
        lifecycle = lifecycle,
    )
    TrackingDiagnosticsContent(
        diagnostics = diagnostics,
        onFixDiagnostic = { diagnostic ->
            context.startActivity(buildDiagnosticIntent(context, diagnostic))
        },
    )
}

@Composable
private fun TrackingDiagnosticsEffects(
    viewModel: TrackingDiagnosticsViewModel,
    lifecycle: Lifecycle,
) {
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }
}

@Composable
private fun TrackingDiagnosticsContent(
    diagnostics: List<PermissionDiagnostic>,
    onFixDiagnostic: (PermissionDiagnostic) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
            )
            Text(
                text = stringResource(R.string.diagnostics_cra_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            if (diagnostics.isEmpty()) {
                EmptyDiagnosticsState()
            } else {
                DiagnosticsList(
                    diagnostics = diagnostics,
                    onFixDiagnostic = onFixDiagnostic,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

private fun buildDiagnosticIntent(
    context: android.content.Context,
    diagnostic: PermissionDiagnostic,
): Intent = when (diagnostic) {
    is PermissionDiagnostic.BatteryOptimizationActive ->
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    else -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    )
}

@Composable
private fun EmptyDiagnosticsState() {
    Text(
        text = stringResource(R.string.diagnostics_no_trip_detected),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticsList(
    diagnostics: List<PermissionDiagnostic>,
    onFixDiagnostic: (PermissionDiagnostic) -> Unit,
) {
    if (diagnostics.all { it is PermissionDiagnostic.AllClear }) {
        AllClearCard()
        return
    }

    diagnostics
        .filterNot { it is PermissionDiagnostic.AllClear }
        .forEach { diagnostic ->
            DiagnosticCard(
                diagnostic = diagnostic,
                onFix = { onFixDiagnostic(diagnostic) },
            )
        }
}

@Composable
private fun AllClearCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.diagnostics_location_granted),
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.diagnostics_battery_unrestricted),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.diagnostics_location_granted),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    diagnostic: PermissionDiagnostic,
    onFix: () -> Unit,
) {
    val (title, explanation) = when (diagnostic) {
        is PermissionDiagnostic.BackgroundLocationMissing ->
            PermissionDiagnostic.BackgroundLocationMissing.TITLE to
                PermissionDiagnostic.BackgroundLocationMissing.EXPLANATION
        is PermissionDiagnostic.BatteryOptimizationActive ->
            PermissionDiagnostic.BatteryOptimizationActive.TITLE to
                PermissionDiagnostic.BatteryOptimizationActive.EXPLANATION
        is PermissionDiagnostic.NotificationPermissionMissing ->
            PermissionDiagnostic.NotificationPermissionMissing.TITLE to
                PermissionDiagnostic.NotificationPermissionMissing.EXPLANATION
        is PermissionDiagnostic.ActivityTransitionRegistrationFailed ->
            PermissionDiagnostic.ActivityTransitionRegistrationFailed.TITLE to
                "${PermissionDiagnostic.ActivityTransitionRegistrationFailed.EXPLANATION} ${diagnostic.reason}"
        is PermissionDiagnostic.AllClear -> return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.diagnostics_battery_restricted),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = onFix,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.diagnostics_open_settings))
            }
        }
    }
}
