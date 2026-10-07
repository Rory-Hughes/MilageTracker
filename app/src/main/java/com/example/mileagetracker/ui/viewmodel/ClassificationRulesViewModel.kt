package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import com.example.mileagetracker.domain.usecase.classification.AutoClassifyPendingTripsUseCase
import com.example.mileagetracker.domain.usecase.classification.ClassificationRule
import com.example.mileagetracker.domain.usecase.classification.DeleteUserRuleUseCase
import com.example.mileagetracker.domain.usecase.classification.SaveUserRuleResult
import com.example.mileagetracker.domain.usecase.classification.SaveUserRuleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ClassificationRulesScreen] and [RuleFormScreen].
 *
 * UI state, form state, and events are all defined in
 * [ClassificationRulesUiState.kt] to avoid redeclaration conflicts.
 *
 * The four private metadata helpers ([toTitle], [toDescription], [toCategory],
 * [toConfidence]) use `else` branches so they remain exhaustive even after
 * [UserRuleClassificationRule] was added as a new [ClassificationRule] subtype.
 * User rules are never passed to these helpers — they only appear in
 * [uiState.userRules] — so the `else` path is unreachable in practice.
 */
@HiltViewModel
class ClassificationRulesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val autoClassifyPendingTripsUseCase: AutoClassifyPendingTripsUseCase,
    private val userRuleRepository: UserClassificationRuleRepository,
    private val saveUserRuleUseCase: SaveUserRuleUseCase,
    private val deleteUserRuleUseCase: DeleteUserRuleUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val ownerUid = currentUserProvider.requireUid()

    // ── Batch run state ───────────────────────────────────────────────
    private val _batchRunning = MutableStateFlow(false)
    private val _batchProgress = MutableStateFlow(0)
    private val _batchTotal = MutableStateFlow(0)
    private val _lastResult = MutableStateFlow<String?>(null)

    // ── Form state ────────────────────────────────────────────────────
    private val _ruleFormState = MutableStateFlow(RuleFormState())
    val ruleFormState: StateFlow<RuleFormState> = _ruleFormState

    // ── Events ────────────────────────────────────────────────────────
    private val _events = MutableSharedFlow<ClassificationRulesEvent>()
    val events = _events.asSharedFlow()

    // ── Combined UI state ──────────────────────────────────────────────
    val uiState: StateFlow<ClassificationRulesUiState> = combine(
        settingsRepository.getDisabledRules(),
        userRuleRepository.observeAll(),
    ) { disabledRules, userRules ->
        Pair(disabledRules, userRules)
    }.combine(
        combine(_batchRunning, _batchProgress, _batchTotal, _lastResult) {
                running, progress, total, last ->
            BatchState(running, progress, total, last)
        }
    ) { (disabledRules, userRules), batch ->
        ClassificationRulesUiState(
            builtInRules = ClassificationRule.ALL.map { rule ->
                RuleUiModel(
                    name = rule.name,
                    title = rule.toTitle(),
                    description = rule.toDescription(),
                    category = rule.toCategory(),
                    confidence = rule.toConfidence(),
                    enabled = rule.name !in disabledRules,
                )
            },
            userRules = userRules,
            isBatchRunning = batch.running,
            batchProgress = batch.progress,
            batchTotal = batch.total,
            lastBatchResult = batch.last,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ClassificationRulesUiState(),
    )

    private data class BatchState(
        val running: Boolean,
        val progress: Int,
        val total: Int,
        val last: String?,
    )

    // ── Built-in rule actions ───────────────────────────────────────────

    val setRuleEnabled: (String, Boolean) -> Unit = { ruleName, enabled ->
        viewModelScope.launch { settingsRepository.setRuleEnabled(ruleName, enabled) }
    }

    val runBatchClassification: () -> Unit = {
        if (!_batchRunning.value) {
            viewModelScope.launch {
            _batchRunning.value = true
            _batchProgress.value = 0
            _batchTotal.value = 0
            _lastResult.value = null

            val result = autoClassifyPendingTripsUseCase(
                driverUid = ownerUid,
                onProgress = { processed, total ->
                    _batchProgress.value = processed
                    _batchTotal.value = total
                },
            )

            _lastResult.value = buildString {
                append("${result.classified} classified")
                if (result.flaggedReview > 0) append(" • ${result.flaggedReview} flagged")
                if (result.noRuleMatched > 0) append(" • ${result.noRuleMatched} unmatched")
                if (result.skipped > 0) append(" • ${result.skipped} skipped")
                if (result.errors > 0) append(" • ${result.errors} errors")
                if (result.total == 0) clear().also { append("No pending trips to classify") }
            }
            _batchRunning.value = false
            }
        }
    }

    // ── User rule CRUD ─────────────────────────────────────────────────

    val loadRuleForEdit: (UserRule?) -> Unit = { rule ->
        _ruleFormState.value = if (rule == null) {
            RuleFormState()
        } else {
            RuleFormState(
                editingId = rule.id,
                name = rule.name,
                keyword = rule.keyword,
                category = rule.category,
                confidencePct = (rule.confidence * 100).toInt().toString(),
                priority = rule.priority.toString(),
                isEnabled = rule.isEnabled,
            )
        }
    }

    val onFormNameChange: (String) -> Unit = { v ->
        _ruleFormState.update { it.copy(name = v, nameError = null) }
    }

    val onFormKeywordChange: (String) -> Unit = { v ->
        _ruleFormState.update {
            it.copy(
                keyword = v,
                keywordError = null
            )
        }
    }
    val onFormCategoryChange: (TripCategory) -> Unit = { v ->
        _ruleFormState.update { it.copy(category = v) }
    }

    val onFormConfidenceChange: (String) -> Unit = { v ->
        _ruleFormState.update {
            it.copy(
                confidencePct = v,
                confidenceError = null
            )
        }
    }
    val onFormPriorityChange: (String) -> Unit = { v ->
        _ruleFormState.update {
            it.copy(
                priority = v,
                priorityError = null
            )
        }
    }
    val onFormEnabledChange: (Boolean) -> Unit = { v ->
        _ruleFormState.update { it.copy(isEnabled = v) }
    }

    val saveRule: () -> Unit = {
        viewModelScope.launch {
            val form = _ruleFormState.value
            val confidenceFloat = form.confidencePct.toFloatOrNull()?.div(100f)
            val priorityInt = form.priority.toIntOrNull()

            var hasError = false
            if (form.name.isBlank()) {
                _ruleFormState.update { it.copy(nameError = "Required") }
                hasError = true
            }
            if (form.keyword.isBlank()) {
                _ruleFormState.update { it.copy(keywordError = "Required") }
                hasError = true
            }
            if (confidenceFloat == null || confidenceFloat !in 0f..1f) {
                _ruleFormState.update { it.copy(confidenceError = "Enter 0–100") }
                hasError = true
            }
            if (priorityInt == null || priorityInt <= 0) {
                _ruleFormState.update { it.copy(priorityError = "Must be > 0") }
                hasError = true
            }
            if (hasError) return@launch

            val rule = UserRule(
                id = form.editingId ?: 0,
                name = form.name.trim(),
                keyword = form.keyword.trim(),
                category = form.category,
                confidence = confidenceFloat!!,
                priority = priorityInt!!,
                isEnabled = form.isEnabled,
            )

            when (val result = saveUserRuleUseCase(rule)) {
                is SaveUserRuleResult.Success -> {
                    _events.emit(ClassificationRulesEvent.RuleSaved)
                    loadRuleForEdit(null)
                }
                is SaveUserRuleResult.ValidationError -> {
                    _events.emit(ClassificationRulesEvent.ShowError(result.message))
                }
            }
        }
    }

    val deleteUserRule: (Int) -> Unit = { ruleId ->
        viewModelScope.launch {
            deleteUserRuleUseCase(ruleId)
            _events.emit(ClassificationRulesEvent.RuleDeleted)
        }
    }

    // ── Built-in rule metadata helpers ────────────────────────────────────
    //
    // These helpers are only ever called with built-in ClassificationRule
    // instances (from ClassificationRule.ALL). UserRuleClassificationRule
    // is handled separately via uiState.userRules. The else branches below
    // satisfy the exhaustiveness requirement introduced when
    // UserRuleClassificationRule joined the sealed hierarchy.

    private fun ClassificationRule.toTitle(): String = when (this) {
        ClassificationRule.MorningCommute -> "Morning Commute"
        ClassificationRule.EveningCommute -> "Evening Commute"
        ClassificationRule.WeekendPersonal -> "Weekend Personal"
        ClassificationRule.LongHaulWeekday -> "Long-Haul Weekday"
        ClassificationRule.TrackingArtefact -> "Tracking Artefact"
        else -> name // UserRuleClassificationRule — unreachable
    }

    private fun ClassificationRule.toDescription(): String = when (this) {
        ClassificationRule.MorningCommute -> "Mon–Fri trips starting 06:30–09:30, between 2 and 60 km"
        ClassificationRule.EveningCommute -> "Mon–Fri trips starting 15:30–19:30, between 2 and 60 km"
        ClassificationRule.WeekendPersonal -> "Saturday or Sunday trips up to 120 km"
        ClassificationRule.LongHaulWeekday -> "Weekday trips over 200 km — likely business travel"
        ClassificationRule.TrackingArtefact -> "Trips under 1 km or shorter than 3 minutes; flagged for review"
        else -> "" // UserRuleClassificationRule — unreachable
    }

    private fun ClassificationRule.toCategory(): TripCategory? = when (this) {
        ClassificationRule.MorningCommute,
        ClassificationRule.EveningCommute -> TripCategory.COMMUTE
        ClassificationRule.WeekendPersonal -> TripCategory.PERSONAL
        ClassificationRule.LongHaulWeekday -> TripCategory.BUSINESS
        ClassificationRule.TrackingArtefact -> null
        else -> null // UserRuleClassificationRule — unreachable
    }

    private fun ClassificationRule.toConfidence(): Float = when (this) {
        ClassificationRule.MorningCommute,
        ClassificationRule.EveningCommute -> 0.72f
        ClassificationRule.WeekendPersonal -> 0.65f
        ClassificationRule.LongHaulWeekday -> 0.58f
        ClassificationRule.TrackingArtefact -> 1.00f
        else -> 0.75f // UserRuleClassificationRule — unreachable
    }
}
