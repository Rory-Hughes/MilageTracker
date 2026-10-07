package com.example.mileagetracker.domain.usecase.expense

import com.example.mileagetracker.domain.model.ExpenseCategory
import com.example.mileagetracker.domain.model.VehicleExpense
import com.example.mileagetracker.domain.repository.VehicleExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * Returns a reactive stream of [VehicleExpense] records for the given
 * [vehicleId], optionally filtered to a specific [year], with
 * [VehicleExpense.businessUseAmount] injected at call time.
 *
 * ## Why businessPercent is a parameter
 * The vehicle's business-use percentage is a *computed* value derived from
 * the annual trip log (business km ÷ total km). It is not stored on
 * [VehicleEntity]. The caller ([VehicleExpenseViewModel]) is responsible for
 * supplying the current percentage — typically obtained from
 * [GetAnnualVehicleSummaryUseCase] or entered by the user in the vehicle
 * settings screen.
 *
 * ## businessUseAmount injection rules
 * - **Parking & Tolls** when [VehicleExpense.isBusinessUse] is `true`:
 *   `businessUseAmount = amountCad` (100 % deductible per CRA T2125).
 * - **All other categories**:
 *   `businessUseAmount = amountCad * (businessPercent / 100)`.
 *
 * @param vehicleId        FK of the vehicle to query.
 * @param year             Optional CRA tax year (e.g. 2026). When null the
 *                         full history is returned.
 * @param businessPercent  Vehicle business-use percentage in the range
 *                         [0.0, 100.0]. Defaults to 0.0 (fully personal).
 */
class GetVehicleExpensesUseCase @Inject constructor(
    private val expenseRepository: VehicleExpenseRepository,
) {

    operator fun invoke(
        vehicleId: Int,
        year: Int? = null,
        businessPercent: Double = 0.0,
    ): Flow<List<VehicleExpense>> {
        val ratio = businessPercent.coerceIn(0.0, 100.0) / 100.0

        val expensesFlow = if (year != null) {
            val (from, to) = yearBounds(year)
            expenseRepository.observeForVehicleInYear(vehicleId, from, to)
        } else {
            expenseRepository.observeForVehicle(vehicleId)
        }

        return expensesFlow.map { expenses ->
            expenses.map { expense ->
                val deductible = when {
                    expense.isBusinessUse &&
                        (
                            expense.category is ExpenseCategory.Parking ||
                                expense.category is ExpenseCategory.Tolls
                            ) -> expense.amountCad
                    else -> expense.amountCad * ratio
                }
                expense.copy(businessUseAmount = deductible)
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun yearBounds(year: Int): Pair<Long, Long> {
        val from = LocalDate.of(year, 1, 1)
            .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val to = LocalDate.of(year, 12, 31)
            .atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
        return from to to
    }
}
