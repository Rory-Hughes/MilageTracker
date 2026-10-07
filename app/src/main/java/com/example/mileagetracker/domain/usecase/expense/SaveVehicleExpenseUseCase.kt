package com.example.mileagetracker.domain.usecase.expense

import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import javax.inject.Inject

/**
 * Validates and persists a [VehicleExpense].
 *
 * ## Validation rules
 * 1. [VehicleExpense.vehicleId] must be > 0.
 * 2. [VehicleExpense.amountCad] must be > 0.
 * 3. [VehicleExpense.date] must be > 0.
 *
 * ## Upsert behaviour
 * - If [VehicleExpense.id] == 0, a new row is inserted and the returned
 *   copy has [VehicleExpense.id] populated from the generated row-id.
 * - If [VehicleExpense.id] > 0, [VehicleExpenseRepository.update] is called
 *   and the original expense is returned unchanged (id is already set).
 */
class SaveVehicleExpenseUseCase @Inject constructor(
    private val repository: VehicleExpenseRepository,
) {

    sealed class Result {
        data class Success(val expense: VehicleExpense) : Result()
        data class ValidationError(val reason: String) : Result()
    }

    suspend operator fun invoke(expense: VehicleExpense): Result =
        validate(expense) ?: saveExpense(expense)

    private suspend fun saveExpense(expense: VehicleExpense): Result =
        if (expense.id == 0) {
            Result.Success(repository.save(expense))
        } else {
            repository.update(expense)
            Result.Success(expense)
        }

    private fun validate(expense: VehicleExpense): Result.ValidationError? =
        when {
            expense.vehicleId <= 0 -> Result.ValidationError("A vehicle must be selected.")
            expense.amountCad <= 0.0 -> Result.ValidationError("Amount must be greater than zero.")
            expense.date <= 0L -> Result.ValidationError("A valid date must be provided.")
            else -> null
        }
}
