package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesEvent
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesUiState
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesViewModel
import com.example.mileagetracker.ui.viewmodel.RuleUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationRulesScreen(
    onNavigateBack: () -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (UserRule) -> Unit,
    viewModel: ClassificationRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    ClassificationRulesEffects(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )
    DeleteRuleDialog(
        pendingDeleteId = pendingDeleteId,
        onDismiss = { pendingDeleteId = null },
        onConfirmDelete = { ruleId ->
            viewModel.deleteUserRule(ruleId)
            pendingDeleteId = null
        },
    )
    ClassificationRulesScaffold(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onCreateRule = onCreateRule,
        onEditRule = onEditRule,
        onToggleBuiltInRule = viewModel.setRuleEnabled,
        onDeleteRule = { pendingDeleteId = it },
        onRunBatch = viewModel.runBatchClassification,
    )
}

@Composable
private fun ClassificationRulesEffects(
    viewModel: ClassificationRulesViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ClassificationRulesEvent.RuleDeleted ->
                    snackbarHostState.showSnackbar("Rule deleted")

                is ClassificationRulesEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message)

                else -> Unit
            }
        }
    }
}

@Composable
private fun DeleteRuleDialog(
    pendingDeleteId: Int?,
    onDismiss: () -> Unit,
    onConfirmDelete: (Int) -> Unit,
) {
    if (pendingDeleteId == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.classification_rule_delete_confirm_title)) },
        text = { Text(stringResource(R.string.classification_rule_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { onConfirmDelete(pendingDeleteId) }) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassificationRulesScaffold(
    state: ClassificationRulesUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onCreateRule: () -> Unit,
    onEditRule: (UserRule) -> Unit,
    onToggleBuiltInRule: (String, Boolean) -> Unit,
    onDeleteRule: (Int) -> Unit,
    onRunBatch: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.classification_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRule) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.classification_rule_add),
                )
            }
        },
    ) { padding ->
        ClassificationRulesContent(
            state = state,
            padding = padding,
            onEditRule = onEditRule,
            onToggleBuiltInRule = onToggleBuiltInRule,
            onDeleteRule = onDeleteRule,
            onRunBatch = onRunBatch,
        )
    }
}

@Composable
internal fun ClassificationRulesContent(
    state: ClassificationRulesUiState,
    padding: PaddingValues,
    onEditRule: (UserRule) -> Unit,
    onToggleBuiltInRule: (String, Boolean) -> Unit,
    onDeleteRule: (Int) -> Unit,
    onRunBatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        BuiltInRulesSection(
            builtInRules = state.builtInRules,
            onToggleBuiltInRule = onToggleBuiltInRule,
        )
        SectionDivider()
        UserRulesSection(
            userRules = state.userRules,
            onEditRule = onEditRule,
            onDeleteRule = onDeleteRule,
        )
        SectionDivider()
        BatchClassificationSection(
            state = state,
            onRunBatch = onRunBatch,
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BuiltInRulesSection(
    builtInRules: List<RuleUiModel>,
    onToggleBuiltInRule: (String, Boolean) -> Unit,
) {
    SectionHeader(
        title = "Built-in Rules",
        subtitle = "Evaluated in priority order. First match wins.",
    )
    builtInRules.forEach { rule ->
        BuiltInRuleCard(
            rule = rule,
            onToggle = { enabled -> onToggleBuiltInRule(rule.name, enabled) },
        )
    }
}

@Composable
private fun UserRulesSection(
    userRules: List<UserRule>,
    onEditRule: (UserRule) -> Unit,
    onDeleteRule: (Int) -> Unit,
) {
    SectionHeader(
        title = "Your Rules",
        subtitle = "Keyword rules you have created. Tap + to add a new one.",
    )
    if (userRules.isEmpty()) {
        Text(
            text = stringResource(R.string.classification_rules_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }

    userRules.forEach { rule ->
        UserRuleCard(
            rule = rule,
            onEdit = { onEditRule(rule) },
            onDelete = { onDeleteRule(rule.id) },
        )
    }
}

@Composable
private fun BatchClassificationSection(
    state: ClassificationRulesUiState,
    onRunBatch: () -> Unit,
) {
    SectionHeader(
        title = "Batch Classification",
        subtitle = "Apply active rules to all unclassified trips.",
    )
    BatchRunPanel(
        isRunning = state.isBatchRunning,
        progress = state.batchProgress,
        total = state.batchTotal,
        lastResult = state.lastBatchResult,
        onRunClicked = onRunBatch,
    )
}
