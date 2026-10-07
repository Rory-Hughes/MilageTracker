package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.UserRule

/**
 * Adapts a [UserRule] into the [ClassificationRule] contract so that
 * user-defined rules participate in the same first-match-wins evaluation
 * chain as built-in rules inside [AutoClassifyTripUseCase].
 *
 * ## Matching logic
 * Fires when [UserRule.keyword] is found (case-insensitive) in any of:
 * - [DriveEntity.startLocation]
 * - [DriveEntity.endLocation]
 * - [DriveEntity.purposeText]
 *
 * The resulting [RuleMatch.category] is always non-null (user rules assign
 * a category rather than flagging for review), so a match with confidence
 * ≥ [AutoClassifyTripUseCase.MIN_CONFIDENCE] will auto-classify the trip.
 *
 * @param userRule The persisted [UserRule] backing this adapter instance.
 */
class UserRuleClassificationRule(
    private val userRule: UserRule,
) : ClassificationRule {

    override val name: String = "UserRule:${userRule.id}:${userRule.name}"
    override val priority: Int = userRule.priority

    override fun evaluate(drive: DriveEntity): RuleMatch? {
        val kw = userRule.keyword.lowercase()
        val hits = listOfNotNull(
            drive.startLocation,
            drive.endLocation,
            drive.purposeText,
        ).any { it.lowercase().contains(kw) }

        return if (hits) {
            RuleMatch(
                rule = this,
                category = userRule.category,
                confidence = userRule.confidence,
            )
        } else {
            null
        }
    }
}
