package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and upserts a [PolicyTemplate].
 *
 * Generates a new UUID for the [id] when [PolicyTemplate.id] is blank (new policy).
 *
 * @throws IllegalArgumentException on validation failure.
 */
class SavePolicyTemplateUseCase @Inject constructor(
    private val repository: PolicyRepository
) {
    suspend operator fun invoke(policy: PolicyTemplate) {
        require(policy.name.isNotBlank()) { "Policy name must not be blank." }
        if (policy.rateMode == RateMode.CUSTOM) {
            require((policy.customRateCentsPerKm ?: 0) > 0) {
                "A positive custom rate (cents/km) is required when rateMode is CUSTOM."
            }
        }
        policy.maxDailyKm?.let {
            require(it > 0) { "maxDailyKm must be positive when set." }
        }

        val toSave = if (policy.id.isBlank()) {
            policy.copy(id = UUID.randomUUID().toString())
        } else {
            policy
        }
        repository.savePolicy(toSave)
    }
}
