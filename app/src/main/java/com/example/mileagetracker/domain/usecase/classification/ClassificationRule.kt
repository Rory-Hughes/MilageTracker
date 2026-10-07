package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import java.util.Calendar

/**
 * Contract for a single deterministic classification rule.
 *
 * ## Rule evaluation model
 * Rules are evaluated in priority order by [AutoClassifyTripUseCase].
 * The first rule whose [evaluate] returns a non-null [RuleMatch] wins;
 * subsequent rules are not consulted (first-match-wins).
 *
 * ## Adding a new rule
 * 1. Create an `object` that implements [ClassificationRule].
 * 2. Give it a unique, stable [name] — this is stored in the audit log.
 * 3. Set [priority]: lower numbers run first.
 * 4. Implement [evaluate]: return a [RuleMatch] if the rule fires, null otherwise.
 * 5. Add the object to [ClassificationRule.ALL] in ascending priority order.
 *
 * @property name      Stable identifier stored in the audit log.
 * @property priority  Evaluation order; lower = higher priority.
 */
sealed interface ClassificationRule {

    val name: String
    val priority: Int

    /**
     * Evaluates this rule against [drive].
     *
     * @return A [RuleMatch] if the rule fires, `null` if conditions are not met.
     */
    fun evaluate(drive: DriveEntity): RuleMatch?

    // ───────────────────────────────────────────────────────────────────
    // Built-in rules
    // ───────────────────────────────────────────────────────────────────

    /**
     * Trips starting Monday–Friday between 06:30–09:30, distance 2–60 km.
     * Strong signal for a regular morning commute.
     */
    object MorningCommute : ClassificationRule {
        override val name = "MorningCommute"
        override val priority = 10

        override fun evaluate(drive: DriveEntity): RuleMatch? {
            val cal = drive.startedAt.toCalendar()
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val timeMinutes = hour * 60 + min
            val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY
            val isMorning = timeMinutes in 390..570 // 06:30 – 09:30
            val distanceOk = drive.distanceKm in 2f..60f
            return if (isWeekday && isMorning && distanceOk) {
                RuleMatch(rule = this, category = TripCategory.COMMUTE, confidence = 0.72f)
            } else {
                null
            }
        }
    }

    /**
     * Trips starting Monday–Friday between 15:30–19:30, distance 2–60 km.
     * Strong signal for a regular evening commute.
     */
    object EveningCommute : ClassificationRule {
        override val name = "EveningCommute"
        override val priority = 20

        override fun evaluate(drive: DriveEntity): RuleMatch? {
            val cal = drive.startedAt.toCalendar()
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val timeMinutes = hour * 60 + min
            val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY
            val isEvening = timeMinutes in 930..1170 // 15:30 – 19:30
            val distanceOk = drive.distanceKm in 2f..60f
            return if (isWeekday && isEvening && distanceOk) {
                RuleMatch(rule = this, category = TripCategory.COMMUTE, confidence = 0.72f)
            } else {
                null
            }
        }
    }

    /**
     * Trips on Saturday or Sunday with distance ≤ 120 km.
     * High probability of personal travel.
     */
    object WeekendPersonal : ClassificationRule {
        override val name = "WeekendPersonal"
        override val priority = 30

        override fun evaluate(drive: DriveEntity): RuleMatch? {
            val cal = drive.startedAt.toCalendar()
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            val distanceOk = drive.distanceKm <= 120f
            return if (isWeekend && distanceOk) {
                RuleMatch(rule = this, category = TripCategory.PERSONAL, confidence = 0.65f)
            } else {
                null
            }
        }
    }

    /**
     * Trips longer than 200 km on a weekday.
     * Unlikely to be a commute; flagged as probable business travel.
     * Lower confidence because personal long-distance trips are also possible.
     */
    object LongHaulWeekday : ClassificationRule {
        override val name = "LongHaulWeekday"
        override val priority = 40

        override fun evaluate(drive: DriveEntity): RuleMatch? {
            val cal = drive.startedAt.toCalendar()
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY
            return if (isWeekday && drive.distanceKm > 200f) {
                RuleMatch(rule = this, category = TripCategory.BUSINESS, confidence = 0.58f)
            } else {
                null
            }
        }
    }

    /**
     * Trips shorter than 1 km or with a duration under 3 minutes (if [endedAt]
     * is known).  Almost certainly a tracking artefact or accidental start.
     * Does not assign a category — instead flags for review.
     */
    object TrackingArtefact : ClassificationRule {
        override val name = "TrackingArtefact"
        override val priority = 5 // Runs before all category rules.

        private const val MIN_DISTANCE_KM = 1f
        private const val MIN_DURATION_MS = 3 * 60 * 1_000L // 3 minutes

        override fun evaluate(drive: DriveEntity): RuleMatch? {
            val tooShortDistance = drive.distanceKm < MIN_DISTANCE_KM
            val tooShortDuration = drive.endedAt != null &&
                (drive.endedAt - drive.startedAt) < MIN_DURATION_MS
            return if (tooShortDistance || tooShortDuration) {
                RuleMatch(
                    rule = this,
                    category = null, // No category — signal NeedsReview.
                    confidence = 1.0f,
                )
            } else {
                null
            }
        }
    }

    companion object {
        /**
         * Ordered list of all active rules.
         * [AutoClassifyTripUseCase] iterates this list in order and stops at
         * the first match.
         */
        val ALL: List<ClassificationRule> = listOf(
            TrackingArtefact, // priority  5 – must run first
            MorningCommute, // priority 10
            EveningCommute, // priority 20
            WeekendPersonal, // priority 30
            LongHaulWeekday, // priority 40
        )
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

/**
 * Outcome of evaluating a single [ClassificationRule] against a trip.
 *
 * @param rule       The rule that fired.
 * @param category   The suggested [TripCategory], or `null` if the rule flags
 *                   the trip for review rather than assigning a category.
 * @param confidence 0.0–1.0 score stored in [DriveEntity.classificationConfidence].
 */
data class RuleMatch(
    val rule: ClassificationRule,
    val category: TripCategory?,
    val confidence: Float,
)

// ── Extension ────────────────────────────────────────────────────────────────

private fun Long.toCalendar(): Calendar =
    Calendar.getInstance().also { it.timeInMillis = this }
