package com.example.mileagetracker.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.ui.viewmodel.AnnualVehicleSummaryUiState
import com.example.mileagetracker.ui.viewmodel.AnnualVehicleSummaryViewModel
import com.example.mileagetracker.ui.viewmodel.ExportUiState
import com.example.mileagetracker.ui.viewmodel.ExportViewModel
import java.io.File

@Composable
fun AnnualVehicleSummaryScreen(
    activeVehicles: List<VehicleEntity>,
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenOdometer: (Int) -> Unit = {},
    viewModel: AnnualVehicleSummaryViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()

    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    val snackbarHostState = remember { SnackbarHostState() }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exportViewModel.onShareConsumed()
    }

    AnnualSummaryExportEffects(
        context = context,
        exportState = exportState,
        snackbarHostState = snackbarHostState,
        onOpenHistory = onOpenHistory,
        onClearError = exportViewModel::clearError,
        onLaunchShare = shareLauncher::launch,
    )

    Scaffold(
        topBar = { AnnualSummaryTopBar(onNavigateBack = onNavigateBack, onOpenHistory = onOpenHistory) },
        floatingActionButton = {
            AnnualSummaryExportFab(
                isVisible = uiState is AnnualVehicleSummaryUiState.Success,
                exportState = exportState,
                selectedFormat = selectedFormat,
                onExport = {
                    exportViewModel.exportLogbook(
                        vehicleId = selectedVehicleId,
                        taxYear = selectedYear,
                        format = selectedFormat,
                        categories = TripCategory.craEligibleCategories.toSet(),
                        includeRouteDetails = true,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        AnnualSummaryScreenContent(
            innerPadding = innerPadding,
            availableYears = viewModel.availableYears,
            selectedYear = selectedYear,
            onYearSelected = viewModel::selectYear,
            activeVehicles = activeVehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = viewModel::selectVehicle,
            showFormatPicker = uiState is AnnualVehicleSummaryUiState.Success,
            selectedFormat = selectedFormat,
            onFormatSelected = { selectedFormat = it },
            uiState = uiState,
            onOpenOdometer = onOpenOdometer,
        )
    }
}

@Composable
private fun AnnualSummaryExportEffects(
    context: Context,
    exportState: ExportUiState,
    snackbarHostState: SnackbarHostState,
    onOpenHistory: () -> Unit,
    onClearError: () -> Unit,
    onLaunchShare: (Intent) -> Unit,
) {
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportUiState.Success -> {
                onLaunchShare(buildAnnualSummaryShareIntent(context, state))
                val result = snackbarHostState.showSnackbar(
                    message = "${state.format.name} saved to report history",
                    actionLabel = "View History",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onOpenHistory()
                }
            }
            is ExportUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                onClearError()
            }
            else -> Unit
        }
    }
}

private fun buildAnnualSummaryShareIntent(
    context: Context,
    state: ExportUiState.Success,
): Intent {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = state.mimeType
        putExtra(Intent.EXTRA_STREAM, resolveAnnualSummaryContentUri(context, state.fileUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(shareIntent, annualSummaryShareChooserTitle(state.format))
}

private fun resolveAnnualSummaryContentUri(
    context: Context,
    fileUri: Uri,
): Uri = runCatching {
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        File(fileUri.path.orEmpty()),
    )
}.getOrElse { fileUri }

private fun annualSummaryShareChooserTitle(format: ExportFormat): String = when (format) {
    ExportFormat.PDF -> "Share Annual Summary PDF"
    ExportFormat.CSV -> "Share Annual Summary CSV"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnualSummaryTopBar(
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.annual_summary_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = stringResource(R.string.report_history_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
private fun AnnualSummaryExportFab(
    isVisible: Boolean,
    exportState: ExportUiState,
    selectedFormat: ExportFormat,
    onExport: () -> Unit,
) {
    if (!isVisible) return

    val isExporting = exportState is ExportUiState.Exporting
    ExtendedFloatingActionButton(
        onClick = {
            if (!isExporting) {
                onExport()
            }
        },
        icon = { AnnualSummaryExportFabIcon(isExporting = isExporting) },
        text = {
            AnnualSummaryExportFabText(
                isExporting = isExporting,
                selectedFormat = selectedFormat,
            )
        },
        containerColor = if (isExporting) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    )
}

@Composable
private fun AnnualSummaryExportFabIcon(isExporting: Boolean) {
    if (isExporting) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = Stroke.thin,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
        )
    }
}

@Composable
private fun AnnualSummaryExportFabText(
    isExporting: Boolean,
    selectedFormat: ExportFormat,
) {
    val label = if (isExporting) {
        "Exporting..."
    } else {
        "Export ${selectedFormat.name}"
    }
    Text(label)
}
