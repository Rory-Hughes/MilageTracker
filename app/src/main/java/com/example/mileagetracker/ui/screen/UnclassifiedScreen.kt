package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.ClassifyState
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.UnclassifiedUiState
import com.example.mileagetracker.util.DriveDisplayModel

/**
 * Swipeable inbox of unclassified drives.
 *
 * ## Task 3.1 changes
 * - [onTripTapped] callback — tapping a card navigates to [TripDetailScreen].
 * - Swipe-to-classify gestures (Business/Personal) retained.
 *
 * ## Task 3.2 changes
 * - [onAddMissedTrip] callback — tapping the “Add missed trip”
 *   [ExtendedFloatingActionButton] navigates to [ManualTripEntryScreen]
 *   in new-trip mode.  The FAB is always visible regardless of the list
 *   state so the user can add a trip even when the inbox is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnclassifiedScreen(
    viewModel: DriveViewModel,
    onTripTapped: (driveId: Int) -> Unit = {},
    onAddMissedTrip: () -> Unit = {},
) {
    val uiState by viewModel.unclassifiedUiState.collectAsStateWithLifecycle()
    val classifyState by viewModel.classifyState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(classifyState) {
        if (classifyState is ClassifyState.Error) {
            snackbarHostState.showSnackbar(
                (classifyState as ClassifyState.Error).message
            )
            viewModel.clearClassifyError()
        }
    }

    UnclassifiedScreenScaffold(
        snackbarHostState = snackbarHostState,
        onAddMissedTrip = onAddMissedTrip,
    ) { innerPadding ->
        UnclassifiedScreenContent(
            innerPadding = innerPadding,
            uiState = uiState,
            onTripTapped = onTripTapped,
            onClassifyDrive = viewModel::classifyDrive,
        )
    }
}

@Composable
private fun UnclassifiedScreenScaffold(
    snackbarHostState: SnackbarHostState,
    onAddMissedTrip: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMissedTrip,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_trip)) },
            )
        },
        content = content,
    )
}

@Composable
private fun UnclassifiedScreenContent(
    innerPadding: PaddingValues,
    uiState: UnclassifiedUiState,
    onTripTapped: (Int) -> Unit,
    onClassifyDrive: (drive: com.example.mileagetracker.data.local.entity.DriveEntity, isBusiness: Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when (val state = uiState) {
            UnclassifiedUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is UnclassifiedUiState.Error -> {
                UnclassifiedErrorState(message = state.message)
            }

            is UnclassifiedUiState.Success -> {
                UnclassifiedSuccessState(
                    drives = state.drives,
                    onTripTapped = onTripTapped,
                    onClassifyDrive = onClassifyDrive,
                )
            }
        }
    }
}

@Composable
private fun UnclassifiedErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.unclassified_error_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun UnclassifiedSuccessState(
    drives: List<com.example.mileagetracker.data.local.entity.DriveEntity>,
    onTripTapped: (Int) -> Unit,
    onClassifyDrive: (drive: com.example.mileagetracker.data.local.entity.DriveEntity, isBusiness: Boolean) -> Unit,
) {
    if (drives.isEmpty()) {
        UnclassifiedEmptyState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(
            items = drives,
            key = { it.id },
        ) { drive ->
            UnclassifiedDriveRow(
                drive = drive,
                onTripTapped = onTripTapped,
                onClassifyDrive = onClassifyDrive,
            )
        }
    }
}

@Composable
private fun UnclassifiedEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.unclassified_empty_title),
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.unclassified_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.unclassified_empty_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UnclassifiedDriveRow(
    drive: com.example.mileagetracker.data.local.entity.DriveEntity,
    onTripTapped: (Int) -> Unit,
    onClassifyDrive: (drive: com.example.mileagetracker.data.local.entity.DriveEntity, isBusiness: Boolean) -> Unit,
) {
    val display = DriveDisplayModel.from(drive)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onClassifyDrive(drive, true)
                    true
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    onClassifyDrive(drive, false)
                    true
                }

                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(dismissDirection = dismissState.dismissDirection)
        },
        content = {
            DriveCard(
                display = display,
                onClick = { onTripTapped(drive.id) },
            )
        },
    )
}

@Composable
private fun SwipeBackground(dismissDirection: SwipeToDismissBoxValue) {
    val backgroundColor = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
        SwipeToDismissBoxValue.EndToStart -> Color(0xFF9E9E9E)
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }
    val alignment = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart,
        SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
    }
    val icon = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.BusinessCenter
        SwipeToDismissBoxValue.EndToStart,
        SwipeToDismissBoxValue.Settled -> Icons.Filled.Person
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.lg),
        contentAlignment = alignment,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun DriveCard(
    display: DriveDisplayModel,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(
                    text = "${display.dateLabel} \u2022 ${display.timeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = display.distanceLabel,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(display.entity.startLocation, style = MaterialTheme.typography.bodyLarge)
            Text("\u2193", modifier = Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.xs))
            Text(display.entity.endLocation, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
