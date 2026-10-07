package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesEvent
import com.example.mileagetracker.ui.viewmodel.ClassificationRulesViewModel
import com.example.mileagetracker.ui.viewmodel.RuleFormState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleFormScreen(
    ruleToEdit: UserRule?,
    onNavigateBack: () -> Unit,
    viewModel: ClassificationRulesViewModel = hiltViewModel(),
) {
    val formState by viewModel.ruleFormState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    RuleFormEffects(
        ruleToEdit = ruleToEdit,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
    )
    RuleFormScaffold(
        formState = formState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNameChange = viewModel.onFormNameChange,
        onKeywordChange = viewModel.onFormKeywordChange,
        onCategoryChange = viewModel.onFormCategoryChange,
        onConfidenceChange = viewModel.onFormConfidenceChange,
        onPriorityChange = viewModel.onFormPriorityChange,
        onEnabledChange = viewModel.onFormEnabledChange,
        onSave = viewModel.saveRule,
    )
}

@Composable
private fun RuleFormEffects(
    ruleToEdit: UserRule?,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: ClassificationRulesViewModel,
) {
    LaunchedEffect(ruleToEdit) {
        viewModel.loadRuleForEdit(ruleToEdit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ClassificationRulesEvent.RuleSaved -> onNavigateBack()
                is ClassificationRulesEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleFormScaffold(
    formState: RuleFormState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onCategoryChange: (com.example.mileagetracker.data.local.entity.TripCategory) -> Unit,
    onConfidenceChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (formState.isEditMode) "Edit Rule" else "New Rule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        RuleFormContent(
            formState = formState,
            padding = padding,
            onNameChange = onNameChange,
            onKeywordChange = onKeywordChange,
            onCategoryChange = onCategoryChange,
            onConfidenceChange = onConfidenceChange,
            onPriorityChange = onPriorityChange,
            onEnabledChange = onEnabledChange,
            onSave = onSave,
        )
    }
}

@Composable
private fun RuleFormContent(
    formState: RuleFormState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onNameChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onCategoryChange: (com.example.mileagetracker.data.local.entity.TripCategory) -> Unit,
    onConfidenceChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        RuleNameField(
            value = formState.name,
            error = formState.nameError,
            onValueChange = onNameChange,
        )
        RuleKeywordField(
            value = formState.keyword,
            error = formState.keywordError,
            onValueChange = onKeywordChange,
        )
        CategoryDropdown(
            selected = formState.category,
            onSelect = onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
        )
        ConfidenceField(
            value = formState.confidencePct,
            error = formState.confidenceError,
            onValueChange = onConfidenceChange,
        )
        PriorityField(
            value = formState.priority,
            error = formState.priorityError,
            onValueChange = onPriorityChange,
        )
        EnabledSection(
            isEnabled = formState.isEnabled,
            onCheckedChange = onEnabledChange,
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (formState.isEditMode) "Save Changes" else "Create Rule")
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}
