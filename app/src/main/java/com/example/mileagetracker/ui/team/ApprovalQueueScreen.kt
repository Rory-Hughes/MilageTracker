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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.theme.Stroke
import com.example.mileagetracker.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalQueueScreen(
    reviewerUserId: Long,
    viewModel: ApprovalQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        val msg = uiState.errorMessage ?: uiState.successMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Approval Queue") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.pendingRequests.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("No pending approvals.", style = MaterialTheme.typography.bodyLarge) }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { Spacer(Modifier.height(Spacing.sm)) }
                items(uiState.pendingRequests, key = { it.id }) { request ->
                    ApprovalRequestCard(
                        request = request,
                        onApprove = { viewModel.approve(request.id, reviewerUserId) },
                        onReject = { comment -> viewModel.reject(request.id, reviewerUserId, comment) },
                        onReturn = { comment -> viewModel.returnForRevision(request.id, reviewerUserId, comment) },
                    )
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun ApprovalRequestCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: (comment: String) -> Unit,
    onReturn: (comment: String) -> Unit,
) {
    var showCommentDialog by remember { mutableStateOf<DialogType?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Stroke.thin),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = if (request.tripId != null) "Trip #${request.tripId}" else "Expense #${request.expenseId}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Submitted ${DateFormats.uiDate(request.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                IconButton(onClick = { showCommentDialog = DialogType.RETURN }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Return for revision")
                }
                IconButton(onClick = { showCommentDialog = DialogType.REJECT }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Reject",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onApprove) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Approve",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showCommentDialog != null) {
        CommentDialog(
            title = if (showCommentDialog == DialogType.REJECT) "Reject Request" else "Return for Revision",
            confirmLabel = if (showCommentDialog == DialogType.REJECT) "Reject" else "Return",
            onConfirm = { comment ->
                if (showCommentDialog == DialogType.REJECT) onReject(comment) else onReturn(comment)
                showCommentDialog = null
            },
            onDismiss = { showCommentDialog = null },
        )
    }
}

private enum class DialogType { REJECT, RETURN }

@Composable
private fun CommentDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Comment") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (comment.isNotBlank()) onConfirm(comment) },
                enabled = comment.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
