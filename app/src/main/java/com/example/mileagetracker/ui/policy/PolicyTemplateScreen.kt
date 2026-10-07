package com.example.mileagetracker.ui.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyTemplateScreen(
    onNavigateUp: () -> Unit,
    viewModel: PolicyTemplateViewModel = hiltViewModel(),
) {
    val policies by viewModel.policies.collectAsState()
    var editingPolicy by remember { mutableStateOf<PolicyTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<PolicyTemplate?>(null) }

    PolicyTemplateScaffold(
        policies = policies,
        onNavigateUp = onNavigateUp,
        onCreatePolicy = {
            editingPolicy = emptyPolicyTemplate()
        },
        onEditPolicy = { editingPolicy = it },
        onDeletePolicy = { deleteTarget = it },
    )
    PolicyTemplateDialogs(
        editingPolicy = editingPolicy,
        deleteTarget = deleteTarget,
        onSavePolicy = {
            viewModel.savePolicy(it)
            editingPolicy = null
        },
        onDismissEditor = { editingPolicy = null },
        onConfirmDelete = {
            viewModel.deletePolicy(it.id)
            deleteTarget = null
        },
        onDismissDelete = { deleteTarget = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyTemplateScaffold(
    policies: List<PolicyTemplate>,
    onNavigateUp: () -> Unit,
    onCreatePolicy: () -> Unit,
    onEditPolicy: (PolicyTemplate) -> Unit,
    onDeletePolicy: (PolicyTemplate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Templates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePolicy) {
                Icon(Icons.Default.Add, contentDescription = "Create policy")
            }
        },
    ) { padding ->
        PolicyTemplateContent(
            policies = policies,
            padding = padding,
            onEditPolicy = onEditPolicy,
            onDeletePolicy = onDeletePolicy,
        )
    }
}

@Composable
private fun PolicyTemplateContent(
    policies: List<PolicyTemplate>,
    padding: PaddingValues,
    onEditPolicy: (PolicyTemplate) -> Unit,
    onDeletePolicy: (PolicyTemplate) -> Unit,
) {
    if (policies.isEmpty()) {
        EmptyPolicyTemplateState(padding = padding)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(policies, key = { it.id }) { policy ->
            PolicyTemplateRow(
                policy = policy,
                onEdit = { onEditPolicy(policy) },
                onDelete = { onDeletePolicy(policy) },
            )
        }
    }
}

@Composable
private fun EmptyPolicyTemplateState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No policy templates yet.\nTap + to create one.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PolicyTemplateDialogs(
    editingPolicy: PolicyTemplate?,
    deleteTarget: PolicyTemplate?,
    onSavePolicy: (PolicyTemplate) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: (PolicyTemplate) -> Unit,
    onDismissDelete: () -> Unit,
) {
    editingPolicy?.let { current ->
        PolicyEditorSheet(
            initial = current,
            onSave = onSavePolicy,
            onDismiss = onDismissEditor,
        )
    }

    deleteTarget?.let { policy ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete policy?") },
            text = { Text("\"${policy.name}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(policy) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun emptyPolicyTemplate() = PolicyTemplate(
    id = "",
    name = "",
    rateMode = RateMode.CRA_STANDARD,
)
