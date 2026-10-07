package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.TripCategory

/**
 * Domain model for a user-defined classification rule.
 *
 * ## Matching logic
 * A [UserRule] fires when the trip's `startLocation`, `endLocation`, or
 * `purposeText` contains [keyword] (case-insensitive).
 *
 * User rules are evaluated **after** all built-in [ClassificationRule]s
 * unless [priority] is set low enough to override them. The default priority
 * of 50 places them after all current built-ins (max priority 40).
 *
 * @param id          DB-assigned primary key (0 = unsaved).
 * @param name        Short user-facing label (e.g. "Client ABC").
 * @param keyword     Substring matched case-insensitively against trip location
 *                    and purpose fields.
 * @param category    [TripCategory] to assign when the rule fires.
 * @param confidence  0.0–1.0 confidence score passed to [AutoClassifyTripUseCase].
 *                    Must be ≥ [AutoClassifyTripUseCase.MIN_CONFIDENCE] (0.55) to
 *                    result in auto-classification rather than review-flagging.
 * @param priority    Evaluation order relative to built-in rules; lower = earlier.
 *                    Default 50 runs after all current built-ins.
 * @param isEnabled   Whether the rule participates in auto-classification.
 * @param createdAt   Epoch millis of row insertion.
 */
data class UserRule(
    val id: Int = 0,
    val name: String,
    val keyword: String,
    val category: TripCategory,
    val confidence: Float = 0.75f,
    val priority: Int = 50,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
