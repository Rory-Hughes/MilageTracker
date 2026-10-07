package com.example.mileagetracker.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.ui.viewmodel.ExportScreenViewModel
import com.example.mileagetracker.ui.viewmodel.ExportUiState
import com.example.mileagetracker.ui.viewmodel.ExportViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit = {},
    filterViewModel: ExportScreenViewModel = hiltViewModel(),
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedYear by filterViewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedVehicleId by filterViewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedCategories by filterViewModel.selectedCategories.collectAsStateWithLifecycle()
    val activeVehicles by filterViewModel.activeVehicles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFormat by remember { mutableStateOf(ExportFormat.PDF) }

    val exportAction = {
        viewModel.exportLogbook(
            vehicleId = selectedVehicleId,
            taxYear = selectedYear,
            format = selectedFormat,
            categories = selectedCategories,
            includeRouteDetails = true,
        )
    }

    ExportScreenEffects(
        context = context,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRetry = exportAction,
        onShareConsumed = viewModel::onShareConsumed,
        onClearError = viewModel::clearError,
    )

    Scaffold(
        topBar = { ExportScreenTopBar(onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ExportScreenContent(
            innerPadding = innerPadding,
            availableYears = filterViewModel.availableYears,
            selectedYear = selectedYear,
            onYearSelected = filterViewModel::selectYear,
            activeVehicles = activeVehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = filterViewModel::selectVehicle,
            selectedCategories = selectedCategories,
            filterableCategories = filterViewModel.filterableCategories,
            onToggleCategory = filterViewModel::toggleCategory,
            selectedFormat = selectedFormat,
            onFormatSelected = { selectedFormat = it },
            isExporting = uiState is ExportUiState.Exporting,
            onExport = exportAction,
        )
    }
}

@Composable
private fun ExportScreenEffects(
    context: Context,
    uiState: ExportUiState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onShareConsumed: () -> Unit,
    onClearError: () -> Unit,
) {
    val pdfShareTitle = stringResource(R.string.cra_logbook_share_pdf_title)
    val csvShareTitle = stringResource(R.string.cra_logbook_share_csv_title)
    val retryLabel = stringResource(R.string.action_retry)

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ExportUiState.Success -> {
                context.startActivity(
                    Intent.createChooser(
                        buildExportShareIntent(context, state),
                        shareTitleFor(state.format, pdfShareTitle, csvShareTitle),
                    )
                )
                onShareConsumed()
            }
            is ExportUiState.Error -> {
                val result = snackbarHostState.showSnackbar(
                    message = state.message,
                    actionLabel = retryLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onRetry() else onClearError()
            }
            else -> Unit
        }
    }
}

private fun buildExportShareIntent(
    context: Context,
    state: ExportUiState.Success,
): Intent = Intent(Intent.ACTION_SEND).apply {
    type = state.mimeType
    putExtra(
        Intent.EXTRA_STREAM,
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            File(requireNotNull(state.fileUri.path)),
        )
    )
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun shareTitleFor(
    format: ExportFormat,
    pdfShareTitle: String,
    csvShareTitle: String,
): String = when (format) {
    ExportFormat.PDF -> pdfShareTitle
    ExportFormat.CSV -> csvShareTitle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportScreenTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.export_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
    )
}
