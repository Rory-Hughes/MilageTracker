package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.CraLogbookUiState

@Composable
internal fun CraLogbookStateContent(
    uiState: CraLogbookUiState,
    onTripTapped: (Int) -> Unit,
) {
    when (val state = uiState) {
        CraLogbookUiState.Loading -> CraLogbookLoadingState()
        is CraLogbookUiState.Error -> CraLogbookErrorState(message = state.message)
        is CraLogbookUiState.Success -> {
            if (state.entries.isEmpty()) {
                CraLogbookEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(
                        items = state.entries,
                        key = { it.tripId },
                    ) { entry ->
                        LogbookEntryRow(
                            entry = entry,
                            onTap = { onTripTapped(entry.tripId) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CraLogbookLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CraLogbookErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CraLogbookEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.cra_logbook_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.cra_logbook_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
