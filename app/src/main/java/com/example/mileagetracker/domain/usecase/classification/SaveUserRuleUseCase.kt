package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import javax.inject.Inject

/**
 * Validates and persists a [UserRule].
 *
 * ## Validation rules
 * - [UserRule.name] must not be blank.
 * - [UserRule.keyword] must not be blank.
 * - [UserRule.confidence] must be in 0.0..1.0.
 * - [UserRule.priority] must be > 0.
 *
 * @return [SaveUserRuleResult.Success] with the saved rule on success, or
 *         [SaveUserRuleResult.ValidationError] describing the first violation.
 */
class SaveUserRuleUseCase @Inject constructor(
    private val repository: UserClassificationRuleRepository,
) {
    suspend operator fun invoke(rule: UserRule): SaveUserRuleResult =
        validate(rule) ?: SaveUserRuleResult.Success(repository.save(rule))

    private fun validate(rule: UserRule): SaveUserRuleResult.ValidationError? =
        when {
            rule.name.isBlank() -> SaveUserRuleResult.ValidationError("Rule name must not be empty")
            rule.keyword.isBlank() -> SaveUserRuleResult.ValidationError("Keyword must not be empty")
            rule.confidence !in 0f..1f ->
                SaveUserRuleResult.ValidationError("Confidence must be between 0 and 1")
            rule.priority <= 0 ->
                SaveUserRuleResult.ValidationError("Priority must be a positive number")
            else -> null
        }
}

sealed interface SaveUserRuleResult {
    data class Success(val rule: UserRule) : SaveUserRuleResult
    data class ValidationError(val message: String) : SaveUserRuleResult
}
