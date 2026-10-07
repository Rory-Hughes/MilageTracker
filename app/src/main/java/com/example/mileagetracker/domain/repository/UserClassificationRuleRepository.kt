package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.UserRule
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for user-defined classification rules.
 *
 * All write operations return the affected [UserRule] (with its DB-assigned [id]
 * populated on insert) so callers can immediately use the new id without
 * a follow-up fetch.
 */
interface UserClassificationRuleRepository {

    /** Reactive stream of all rules — used to populate [ClassificationRulesScreen]. */
    fun observeAll(): Flow<List<UserRule>>

    /**
     * One-shot fetch of all enabled rules.
     * Called by [AutoClassifyTripUseCase] at classification time.
     */
    suspend fun getEnabledRules(): List<UserRule>

    /**
     * Insert a new rule or replace an existing one (matched by [UserRule.id]).
     * @return The saved rule with its DB-assigned id.
     */
    suspend fun save(rule: UserRule): UserRule

    /** Hard-delete a rule by [id]. No-op if the rule does not exist. */
    suspend fun deleteById(id: Int)
}
