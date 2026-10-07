package com.example.mileagetracker.ui.screen

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.ui.viewmodel.ReportHistoryListItem
import com.example.mileagetracker.ui.viewmodel.ReportHistoryViewModel
import com.example.mileagetracker.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ReportHistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val groupedReports by viewModel.groupedReports.collectAsStateWithLifecycle()
    val retentionWarn by viewModel.retentionWarning.collectAsStateWithLifecycle()
    val latestReport: ReportHistoryListItem? = groupedReports
        .values
        .firstOrNull()
        ?.firstOrNull()

    RetentionWarningDialog(
        report = retentionWarn,
        onDismiss = viewModel::clearRetentionWarning,
    )
    ReportHistoryScaffold(
        groupedReports = groupedReports,
        latestReport = latestReport,
        onNavigateBack = onNavigateBack,
        onShareReport = { viewModel.reshare(context, it) },
        onDeleteReport = viewModel::deleteReport,
    )
}

@Composable
private fun RetentionWarningDialog(
    report: ReportHistoryEntity?,
    onDismiss: () -> Unit,
) {
    report?.let { current ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cannot Delete Report") },
            text = {
                Text(
                    "This ${current.format} report for tax year ${current.year} was generated on " +
                        "${formatDate(current.generatedAt)}.\n\n" +
                        "CRA requires motor vehicle records to be retained for at least 6 years " +
                        "from the end of the tax year to which they relate. This report is still " +
                        "within that window and cannot be deleted yet.",
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportHistoryScaffold(
    groupedReports: Map<Int, List<ReportHistoryListItem>>,
    latestReport: ReportHistoryListItem?,
    onNavigateBack: () -> Unit,
    onShareReport: (ReportHistoryEntity) -> Unit,
    onDeleteReport: (ReportHistoryEntity) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.report_history_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
        )

        if (groupedReports.isEmpty()) {
            EmptyHistoryContent()
        } else {
            ReportHistoryList(
                groupedReports = groupedReports,
                latestReport = latestReport,
                onShareReport = onShareReport,
                onDeleteReport = onDeleteReport,
            )
        }
    }
}

@Composable
private fun ReportHistoryList(
    groupedReports: Map<Int, List<ReportHistoryListItem>>,
    latestReport: ReportHistoryListItem?,
    onShareReport: (ReportHistoryEntity) -> Unit,
    onDeleteReport: (ReportHistoryEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        latestReport?.let { latest ->
            item(key = "share_last_export") {
                ShareLatestExportButton(
                    latest = latest,
                    onShare = { onShareReport(latest.report) },
                )
            }
        }

        groupedReports.forEach { (year, reports) ->
            item(key = "header_$year") {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            items(
                items = reports,
                key = { it.report.id },
            ) { item ->
                ReportHistoryRow(
                    item = item,
                    onShare = { onShareReport(item.report) },
                    onDelete = { onDeleteReport(item.report) },
                )
            }
        }
    }
}

@Composable
private fun ShareLatestExportButton(
    latest: ReportHistoryListItem,
    onShare: () -> Unit,
) {
    Button(
        onClick = onShare,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(
            text = "Share Last Export - ${latest.report.format.uppercase()} ${latest.report.year}",
        )
    }
}

@Composable
private fun ReportHistoryRow(
    item: ReportHistoryListItem,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatBadge(format = item.report.format)
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = formatDate(item.report.generatedAt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = item.vehicleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatFileSize(item.report.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.report_history_reshare),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.report_history_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(format: String) {
    val isPdf = format.uppercase() == "PDF"
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = format.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (isPdf) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            labelColor = if (isPdf) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    )
}

@Composable
private fun EmptyHistoryContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.report_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.report_history_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(epochMs: Long): String = DateFormats.uiDateTime(epochMs)

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
    else -> "${bytes / (1_024 * 1_024)} MB"
}
