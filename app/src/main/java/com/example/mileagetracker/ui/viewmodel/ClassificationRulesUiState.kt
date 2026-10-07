package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.UserRule

/**
 * Presentation model for a single built-in [ClassificationRule] row.
 *
 * @param name        Stable rule identifier (matches [ClassificationRule.name]).
 * @param title       Short human-readable title shown as the row heading.
 * @param description Plain-English explanation of when the rule fires.
 * @param category    The [TripCategory] the rule assigns, or null for rules
 *                    that flag for review (e.g. TrackingArtefact).
 * @param confidence  0.0–1.0 confidence score the rule emits on a match.
 * @param enabled     Whether the user has this rule switched on.
 */
data class RuleUiModel(
    val name: String,
    val title: String,
    val description: String,
    val category: TripCategory?,
    val confidence: Float,
    val enabled: Boolean,
)

/**
 * Full UI state for [ClassificationRulesScreen].
 *
 * @param builtInRules   Ordered list of built-in rules with their toggle states.
 * @param userRules      Ordered list of user-defined keyword rules.
 * @param isBatchRunning True while [AutoClassifyPendingTripsUseCase] is executing.
 * @param batchProgress  0–[batchTotal] counter updated during a batch run.
 * @param batchTotal     Total number of trips to process in the current run.
 * @param lastBatchResult Human-readable summary of the most recent batch run.
 *                        Null before any run has been triggered this session.
 */
data class ClassificationRulesUiState(
    val builtInRules: List<RuleUiModel> = emptyList(),
    val userRules: List<UserRule> = emptyList(),
    val isBatchRunning: Boolean = false,
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val lastBatchResult: String? = null,
)

/**
 * Form state for [RuleFormScreen].
 *
 * @param editingId       PK of the rule being edited, or null in create mode.
 * @param name            Current value of the rule-name field.
 * @param keyword         Current value of the keyword field.
 * @param category        Currently selected [TripCategory].
 * @param confidencePct   Confidence as a percentage string ("0"–"100").
 * @param priority        Priority as a positive-integer string.
 * @param isEnabled       Current state of the enabled toggle.
 * @param nameError       Inline validation error for the name field, or null.
 * @param keywordError    Inline validation error for the keyword field, or null.
 * @param confidenceError Inline validation error for the confidence field, or null.
 * @param priorityError   Inline validation error for the priority field, or null.
 */
data class RuleFormState(
    val editingId: Int? = null,
    val name: String = "",
    val keyword: String = "",
    val category: TripCategory = TripCategory.BUSINESS,
    val confidencePct: String = "75",
    val priority: String = "50",
    val isEnabled: Boolean = true,
    val nameError: String? = null,
    val keywordError: String? = null,
    val confidenceError: String? = null,
    val priorityError: String? = null,
) {
    val isEditMode: Boolean get() = editingId != null
}

/** One-shot events emitted by [ClassificationRulesViewModel]. */
sealed interface ClassificationRulesEvent {
    data object RuleSaved : ClassificationRulesEvent
    data object RuleDeleted : ClassificationRulesEvent
    data class ShowError(val message: String) : ClassificationRulesEvent
}
