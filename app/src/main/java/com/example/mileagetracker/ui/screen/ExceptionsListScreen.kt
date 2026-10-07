package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.model.MissingField
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.ui.common.EmptyStateView
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsListScreen(
    incompleteTrips: List<ReportReadiness>,
    exceptions: List<TripException> = emptyList(),
    onTripTapped: (tripId: Int) -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val filteredState = remember(selectedTab, incompleteTrips, exceptions) {
        filteredExceptionsState(
            selectedTab = selectedTab,
            incompleteTrips = incompleteTrips,
            exceptions = exceptions,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ExceptionsTopBar(onNavigateBack = onNavigateBack)
        ExceptionsTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        ExceptionsContent(
            state = filteredState,
            onTripTapped = onTripTapped,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionsTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.exceptions_list_title)) },
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

@Composable
private fun ExceptionsTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = Spacing.lg,
    ) {
        EXCEPTION_TABS.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun ExceptionsContent(
    state: FilteredExceptionsState,
    onTripTapped: (Int) -> Unit,
) {
    if (state.isEmpty) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            title = stringResource(R.string.exceptions_list_empty_title),
            body = stringResource(R.string.exceptions_list_empty_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.incompleteTrips.isNotEmpty()) {
            item {
                ExceptionsReadinessHeader(count = state.incompleteTrips.size)
            }
            items(
                items = state.incompleteTrips,
                key = { "readiness_${it.tripId}" },
            ) { readiness ->
                ExceptionRow(
                    readiness = readiness,
                    onTripTapped = onTripTapped,
                )
            }
        }

        if (state.exceptions.isNotEmpty()) {
            if (state.incompleteTrips.isNotEmpty()) {
                item { Spacer(Modifier.height(Spacing.sm)) }
            }
            item {
                ExceptionsFlaggedHeader(count = state.exceptions.size)
            }
            items(
                items = state.exceptions,
                key = { "exception_${it.tripId}_${it.exceptionType.name}" },
            ) { exception ->
                TripExceptionRow(
                    exception = exception,
                    onTripTapped = onTripTapped,
                )
            }
        }
    }
}

@Composable
private fun ExceptionsReadinessHeader(count: Int) {
    Text(
        text = pluralStringResource(
            R.plurals.exceptions_list_subtitle,
            count,
            count,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun ExceptionsFlaggedHeader(count: Int) {
    Text(
        text = pluralStringResource(
            R.plurals.exceptions_flagged_subtitle,
            count,
            count,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xs))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExceptionRow(
    readiness: ReportReadiness,
    onTripTapped: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTripTapped(readiness.tripId) },
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.exceptions_row_trip_id, readiness.tripId),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.exceptions_row_score,
                        (readiness.readinessScore * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.md))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                readiness.missingFields.forEach { field ->
                    MissingFieldChip(field)
                }
            }
        }
    }
}

@Composable
private fun TripExceptionRow(
    exception: TripException,
    onTripTapped: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTripTapped(exception.tripId) },
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.exceptions_row_trip_id, exception.tripId),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = exception.exceptionType.toLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun MissingFieldChip(field: MissingField) {
    val label = when (field) {
        MissingField.PURPOSE -> stringResource(R.string.missing_field_purpose)
        MissingField.VEHICLE -> stringResource(R.string.missing_field_vehicle)
        MissingField.CATEGORY -> stringResource(R.string.missing_field_category)
        MissingField.DISTANCE -> stringResource(R.string.missing_field_distance)
        MissingField.ATTACHMENT -> stringResource(R.string.missing_field_attachment)
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    )
}

private fun ExceptionType.toLabel(): String = when (this) {
    ExceptionType.DUPLICATE_FLAGGED -> "May be a duplicate trip"
    ExceptionType.INCOMPLETE_DATA -> "Missing required fields"
    ExceptionType.IMPOSSIBLE_ROUTE -> "Route data looks incorrect"
    ExceptionType.MANUAL_OVERRIDE -> "Category override not confirmed"
    ExceptionType.NEEDS_REVIEW -> "Flagged for manual review"
}

private fun filteredExceptionsState(
    selectedTab: Int,
    incompleteTrips: List<ReportReadiness>,
    exceptions: List<TripException>,
): FilteredExceptionsState {
    val filteredIncomplete = when (selectedTab) {
        0, 1 -> incompleteTrips
        else -> emptyList()
    }
    val filteredExceptions = when (selectedTab) {
        0 -> exceptions
        1 -> emptyList()
        else -> EXCEPTION_TABS[selectedTab].filter
            ?.let { type -> exceptions.filter { it.exceptionType == type } }
            ?: exceptions
    }
    return FilteredExceptionsState(
        incompleteTrips = filteredIncomplete,
        exceptions = filteredExceptions,
    )
}

private data class ExceptionTabDef(
    val label: String,
    val filter: ExceptionType?,
)

private data class FilteredExceptionsState(
    val incompleteTrips: List<ReportReadiness>,
    val exceptions: List<TripException>,
) {
    val isEmpty: Boolean
        get() = incompleteTrips.isEmpty() && exceptions.isEmpty()
}

private val EXCEPTION_TABS = listOf(
    ExceptionTabDef("All", filter = null),
    ExceptionTabDef("Incomplete", filter = null),
    ExceptionTabDef("Duplicates", filter = ExceptionType.DUPLICATE_FLAGGED),
    ExceptionTabDef("Needs Review", filter = ExceptionType.NEEDS_REVIEW),
    ExceptionTabDef("Route Issues", filter = ExceptionType.IMPOSSIBLE_ROUTE),
    ExceptionTabDef("Overrides", filter = ExceptionType.MANUAL_OVERRIDE),
)
