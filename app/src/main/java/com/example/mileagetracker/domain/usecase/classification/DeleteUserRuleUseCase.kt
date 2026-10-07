package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import javax.inject.Inject

/**
 * Deletes a user-defined classification rule by its database id.
 *
 * This is a hard delete — the row is removed from the
 * `user_classification_rules` table immediately.  Trips that were
 * previously auto-classified by this rule retain their audit rows
 * (the rule name is stored as a string in the audit log), but future
 * trips will no longer match this rule.
 *
 * @param ruleId The [UserRule.id] to delete.
 */
class DeleteUserRuleUseCase @Inject constructor(
    private val repository: UserClassificationRuleRepository,
) {
    suspend operator fun invoke(ruleId: Int) = repository.deleteById(ruleId)
}
