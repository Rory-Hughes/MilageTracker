package com.example.mileagetracker.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.util.DateFormats
import java.time.Month
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodLockScreen(
    managerUserId: Long,
    viewModel: PeriodLockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Period Locks") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { Spacer(Modifier.height(Spacing.sm)) }
                items(uiState.periodRows, key = { "${it.year}-${it.month}" }) { row ->
                    PeriodLockRow(
                        row = row,
                        onLock = { viewModel.lock(row.year, row.month, managerUserId) },
                        onUnlock = { viewModel.unlock(row.year, row.month) },
                    )
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun PeriodLockRow(
    row: PeriodRow,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val label = remember(row.month, row.year, locale) {
        if (row.month != null) {
            "${Month.of(row.month).getDisplayName(TextStyle.FULL, locale)} ${row.year}"
        } else {
            "Full Year ${row.year}"
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                if (row.isLocked && row.lockedAt != null) {
                    Text(
                        text = "Locked ${DateFormats.uiDate(row.lockedAt, locale)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = if (row.isLocked) onUnlock else onLock) {
                Icon(
                    imageVector = if (row.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (row.isLocked) "Unlock" else "Lock",
                    tint = if (row.isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
