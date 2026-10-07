package com.example.mileagetracker.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.ui.viewmodel.CraLogbookUiState
import com.example.mileagetracker.ui.viewmodel.CraLogbookViewModel
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.ui.viewmodel.ExportUiState
import com.example.mileagetracker.ui.viewmodel.ExportViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraLogbookScreen(
    onNavigateBack: () -> Unit,
    onTripTapped: (Int) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenSimplifiedLogbook: () -> Unit = {},
    onOpenAnnualSummary: () -> Unit = {},
    viewModel: CraLogbookViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedCategories by viewModel.selectedCategories.collectAsStateWithLifecycle()
    val activeVehicles by viewModel.activeVehicles.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }
    val snackbarHostState = remember { SnackbarHostState() }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exportViewModel.onShareConsumed()
    }

    val exportAction = {
        exportViewModel.exportLogbook(
            vehicleId = selectedVehicleId,
            taxYear = selectedYear,
            format = selectedFormat,
            categories = selectedCategories,
            includeRouteDetails = true,
        )
    }

    CraLogbookExportEffects(
        context = context,
        exportState = exportState,
        snackbarHostState = snackbarHostState,
        onOpenHistory = onOpenHistory,
        onClearError = exportViewModel::clearError,
        onLaunchShare = shareLauncher::launch,
    )

    CraLogbookScaffold(
        onNavigateBack = onNavigateBack,
        onOpenAnnualSummary = onOpenAnnualSummary,
        onOpenHistory = onOpenHistory,
        onOpenSimplifiedLogbook = onOpenSimplifiedLogbook,
        snackbarHostState = snackbarHostState,
        uiState = uiState,
        exportState = exportState,
        selectedFormat = selectedFormat,
        onExport = exportAction,
        availableYears = viewModel.availableYears,
        selectedYear = selectedYear,
        onYearSelected = viewModel::selectYear,
        selectedMonth = selectedMonth,
        onMonthSelected = viewModel::selectMonth,
        activeVehicles = activeVehicles,
        selectedVehicleId = selectedVehicleId,
        onVehicleSelected = viewModel::selectVehicle,
        filterableCategories = viewModel.filterableCategories,
        selectedCategories = selectedCategories,
        onToggleCategory = viewModel::toggleCategory,
        onFormatSelected = { selectedFormat = it },
        onTripTapped = onTripTapped,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraLogbookScaffold(
    onNavigateBack: () -> Unit,
    onOpenAnnualSummary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSimplifiedLogbook: () -> Unit,
    snackbarHostState: SnackbarHostState,
    uiState: CraLogbookUiState,
    exportState: ExportUiState,
    selectedFormat: ExportFormat,
    onExport: () -> Unit,
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    selectedMonth: Int?,
    onMonthSelected: (Int?) -> Unit,
    activeVehicles: List<com.example.mileagetracker.data.local.entity.VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int?) -> Unit,
    filterableCategories: List<com.example.mileagetracker.data.local.entity.TripCategory>,
    selectedCategories: Set<com.example.mileagetracker.data.local.entity.TripCategory>,
    onToggleCategory: (com.example.mileagetracker.data.local.entity.TripCategory) -> Unit,
    onFormatSelected: (ExportFormat) -> Unit,
    onTripTapped: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            CraLogbookTopBar(
                onNavigateBack = onNavigateBack,
                onOpenAnnualSummary = onOpenAnnualSummary,
                onOpenHistory = onOpenHistory,
                onOpenSimplifiedLogbook = onOpenSimplifiedLogbook,
            )
        },
        bottomBar = {
            (uiState as? CraLogbookUiState.Success)?.summary?.let { summary ->
                LogbookSummaryBar(summary = summary)
            }
        },
        floatingActionButton = {
            CraLogbookExportFab(
                isExporting = exportState is ExportUiState.Exporting,
                selectedFormat = selectedFormat,
                onExport = onExport,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CraLogbookScreenContent(
            innerPadding = innerPadding,
            availableYears = availableYears,
            selectedYear = selectedYear,
            onYearSelected = onYearSelected,
            selectedMonth = selectedMonth,
            onMonthSelected = onMonthSelected,
            activeVehicles = activeVehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = onVehicleSelected,
            filterableCategories = filterableCategories,
            selectedCategories = selectedCategories,
            onToggleCategory = onToggleCategory,
            selectedFormat = selectedFormat,
            onFormatSelected = onFormatSelected,
            uiState = uiState,
            onTripTapped = onTripTapped,
            exportingState = exportState as? ExportUiState.Exporting,
        )
    }
}

@Composable
private fun CraLogbookExportEffects(
    context: Context,
    exportState: ExportUiState,
    snackbarHostState: SnackbarHostState,
    onOpenHistory: () -> Unit,
    onClearError: () -> Unit,
    onLaunchShare: (Intent) -> Unit,
) {
    val sharePdfTitle = stringResource(R.string.cra_logbook_share_pdf_title)
    val shareCsvTitle = stringResource(R.string.cra_logbook_share_csv_title)
    val viewHistoryLabel = stringResource(R.string.cra_logbook_view_history)
    val exportSavedMsg = stringResource(R.string.cra_logbook_export_saved)

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportUiState.Success -> {
                onLaunchShare(
                    Intent.createChooser(
                        buildCraLogbookShareIntent(context, state),
                        craLogbookShareChooserTitle(state.format, sharePdfTitle, shareCsvTitle),
                    )
                )
                val result = snackbarHostState.showSnackbar(
                    message = exportSavedMsg.format(state.format.name),
                    actionLabel = viewHistoryLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onOpenHistory()
            }
            is ExportUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                onClearError()
            }
            else -> Unit
        }
    }
}

private fun buildCraLogbookShareIntent(
    context: Context,
    state: ExportUiState.Success,
): Intent = Intent(Intent.ACTION_SEND).apply {
    type = state.mimeType
    putExtra(Intent.EXTRA_STREAM, resolveCraLogbookContentUri(context, state.fileUri))
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun resolveCraLogbookContentUri(
    context: Context,
    fileUri: Uri,
): Uri = runCatching {
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        File(fileUri.path.orEmpty()),
    )
}.getOrElse { fileUri }

private fun craLogbookShareChooserTitle(
    format: ExportFormat,
    sharePdfTitle: String,
    shareCsvTitle: String,
): String = when (format) {
    ExportFormat.PDF -> sharePdfTitle
    ExportFormat.CSV -> shareCsvTitle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraLogbookTopBar(
    onNavigateBack: () -> Unit,
    onOpenAnnualSummary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSimplifiedLogbook: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.cra_logbook_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenAnnualSummary) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = stringResource(R.string.annual_summary_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = stringResource(R.string.report_history_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onOpenSimplifiedLogbook) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = stringResource(R.string.simplified_logbook_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
private fun CraLogbookExportFab(
    isExporting: Boolean,
    selectedFormat: ExportFormat,
    onExport: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = { if (!isExporting) onExport() },
        icon = {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = Stroke.thin,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                )
            }
        },
        text = {
            if (isExporting) {
                Text(stringResource(R.string.cra_logbook_exporting))
            } else {
                Text(
                    stringResource(R.string.cra_logbook_export_action)
                        .format(selectedFormat.name)
                )
            }
        },
        containerColor = if (isExporting) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    )
}
