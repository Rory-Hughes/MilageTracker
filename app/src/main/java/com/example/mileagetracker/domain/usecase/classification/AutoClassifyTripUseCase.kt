package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import java.util.Calendar
import javax.inject.Inject

/**
 * Rule-based auto-classification engine (Task 4.2 / 4.3b).
 *
 * ## Rule evaluation order
 * 1. Built-in [ClassificationRule.ALL] rules (hardcoded, priority 5–40).
 * 2. User-defined rules loaded from [UserClassificationRuleRepository]
 *    (persisted, priority default 50, configurable).
 *
 * Both sets are merged and sorted by [ClassificationRule.priority] ascending
 * before evaluation. First-match-wins; user rules therefore run after all
 * built-ins unless the user sets a priority < 40.
 *
 * Passing a custom [rules] list to [invoke] bypasses user rules entirely
 * (used by tests and [AutoClassifyPendingTripsUseCase] which passes the
 * merged list itself to avoid a double DB round-trip).
 *
 * @see AutoClassifyResult
 */
class AutoClassifyTripUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
    private val userRuleRepository: UserClassificationRuleRepository,
    private val calculateReimbursementUseCase: CalculateReimbursementUseCase,
) {
    suspend operator fun invoke(
        driveId: Int,
        actorUid: String = SYSTEM_ACTOR,
        force: Boolean = false,
        rules: List<ClassificationRule>? = null,
    ): AutoClassifyResult =
        driveRepository.getDriveById(driveId)?.let { drive ->
            classifyDrive(
                driveId = driveId,
                drive = drive,
                actorUid = actorUid,
                force = force,
                rules = rules,
            )
        } ?: AutoClassifyResult.NotFound

    private suspend fun classifyDrive(
        driveId: Int,
        drive: DriveEntity,
        actorUid: String,
        force: Boolean,
        rules: List<ClassificationRule>?,
    ): AutoClassifyResult = when {
        shouldSkipClassification(drive, force) ->
            AutoClassifyResult.Skipped(drive.classificationStatus)

        else -> resolveRuleMatch(drive, rules)?.let { match ->
            when {
                match.category == null -> flagForReview(driveId, actorUid, drive, match)
                match.confidence < MIN_CONFIDENCE -> AutoClassifyResult.NoRuleMatched
                else -> applyClassification(driveId, actorUid, drive, match, match.category)
            }
        } ?: AutoClassifyResult.NoRuleMatched
    }

    private suspend fun resolveRuleMatch(
        drive: DriveEntity,
        rules: List<ClassificationRule>?,
    ): RuleMatch? = resolveEffectiveRules(rules).firstNotNullOfOrNull { it.evaluate(drive) }

    private suspend fun resolveEffectiveRules(
        rules: List<ClassificationRule>?,
    ): List<ClassificationRule> = rules ?: buildMergedRules()

    private suspend fun buildMergedRules(): List<ClassificationRule> {
        val userAdapters = userRuleRepository.getEnabledRules()
            .map { UserRuleClassificationRule(it) }
        return (ClassificationRule.ALL + userAdapters).sortedBy { it.priority }
    }

    private suspend fun flagForReview(
        driveId: Int,
        actorUid: String,
        drive: DriveEntity,
        match: RuleMatch,
    ): AutoClassifyResult {
        val flagged = drive.copy(
            classificationStatus = ClassificationStatus.NEEDS_REVIEW,
            classificationConfidence = match.confidence,
            updatedAt = currentTimeMillis(),
        )
        driveRepository.updateDrive(flagged)
        writeAuditRow(driveId, actorUid, match, null)
        return AutoClassifyResult.FlaggedForReview(match.rule.name)
    }

    private suspend fun applyClassification(
        driveId: Int,
        actorUid: String,
        drive: DriveEntity,
        match: RuleMatch,
        category: TripCategory,
    ): AutoClassifyResult {
        val financialSnapshot = buildFinancialSnapshot(drive, category)
            ?: return AutoClassifyResult.NoRuleMatched
        val classified = drive.copy(
            tripCategory = category,
            classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
            classificationConfidence = match.confidence,
            appliedRateSnapshot = financialSnapshot.appliedRateSnapshot,
            reimbursementAmountSnapshot = financialSnapshot.reimbursementAmountSnapshot,
            updatedAt = currentTimeMillis(),
        )
        driveRepository.updateDrive(classified)
        writeAuditRow(driveId, actorUid, match, category)
        return AutoClassifyResult.Classified(
            category = category,
            confidence = match.confidence,
            ruleName = match.rule.name,
        )
    }

    private suspend fun buildFinancialSnapshot(
        drive: DriveEntity,
        category: TripCategory,
    ): ClassificationFinancialSnapshot? {
        val reimbursementResult = if (category == TripCategory.BUSINESS) {
            runCatching {
                calculateReimbursementUseCase(
                    taxYear = fiscalYearFor(drive),
                    newDistanceKm = drive.distanceKm,
                )
            }.getOrNull()
        } else {
            null
        }
        return if (category == TripCategory.BUSINESS && reimbursementResult == null) {
            null
        } else {
            ClassificationFinancialSnapshot(
                appliedRateSnapshot = drive.appliedRateSnapshot,
                reimbursementAmountSnapshot = drive.reimbursementAmountSnapshot,
            )
                .copy(
                    appliedRateSnapshot = reimbursementResult?.firstTierRateCents?.div(100f)
                        ?: drive.appliedRateSnapshot,
                    reimbursementAmountSnapshot = reimbursementResult?.amountDollars?.toFloat()
                        ?: drive.reimbursementAmountSnapshot,
                )
        }
    }

    private suspend fun writeAuditRow(
        tripId: Int,
        actorUid: String,
        match: RuleMatch,
        assigned: TripCategory?,
    ) {
        auditRepository.recordEvent(
            tripId = tripId,
            actorUid = actorUid,
            eventType = AuditEventType.AUTO_CLASSIFIED,
            fieldName = "classification_status",
            oldValue = null,
            newValue = buildString {
                append("rule=").append(match.rule.name)
                append(",confidence=").append("%.2f".format(match.confidence))
                assigned?.let { append(",category=").append(it.name) }
            },
        )
    }

    companion object {
        const val SYSTEM_ACTOR = "SYSTEM_CLASSIFIER"
        const val MIN_CONFIDENCE = 0.55f
    }
}

private fun shouldSkipClassification(drive: DriveEntity, force: Boolean): Boolean =
    !force && drive.classificationStatus in HUMAN_STATUSES

private fun fiscalYearFor(drive: DriveEntity): Int =
    Calendar.getInstance()
        .also { it.timeInMillis = drive.startedAt }
        .get(Calendar.YEAR)

private fun currentTimeMillis(): Long = System.currentTimeMillis()

private data class ClassificationFinancialSnapshot(
    val appliedRateSnapshot: Float,
    val reimbursementAmountSnapshot: Float?,
)

private val HUMAN_STATUSES = setOf(
    ClassificationStatus.USER_CONFIRMED,
    ClassificationStatus.USER_CORRECTED,
)

// ── Result sealed hierarchy ────────────────────────────────────────────────────────────

sealed interface AutoClassifyResult {
    data object NotFound : AutoClassifyResult
    data class Skipped(val existingStatus: ClassificationStatus) : AutoClassifyResult
    data object NoRuleMatched : AutoClassifyResult
    data class FlaggedForReview(val ruleName: String) : AutoClassifyResult
    data class Classified(
        val category: TripCategory,
        val confidence: Float,
        val ruleName: String,
    ) : AutoClassifyResult
}
