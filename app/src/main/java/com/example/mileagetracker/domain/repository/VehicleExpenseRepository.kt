package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.VehicleExpense
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for [VehicleExpense] CRUD and aggregate operations.
 *
 * Implemented by [com.example.mileagetracker.data.repository.VehicleExpenseRepositoryImpl] which delegates to
 * [com.example.mileagetracker.data.local.dao.VehicleExpenseDao] for all persistence.
 */
interface VehicleExpenseRepository {

    /**
     * Inserts or replaces [expense] in the database.
     *
     * If [expense.id] is 0 a new row is created; otherwise the existing row
     * is replaced (upsert behaviour via [OnConflictStrategy.REPLACE]).
     *
     * @return A copy of the expense with [VehicleExpense.id] populated from
     *         the inserted row-id.
     */
    suspend fun save(expense: VehicleExpense): VehicleExpense

    /**
     * Updates an existing expense row in place.
     *
     * @return `true` if a row was updated, `false` if the row no longer exists.
     */
    suspend fun update(expense: VehicleExpense): Boolean

    /**
     * Deletes the expense with the given [id].
     */
    suspend fun deleteById(id: Int)

    /**
     * Returns one expense by its primary key, or null if it no longer exists.
     */
    suspend fun getById(id: Int): VehicleExpense?

    /**
     * Reactive stream of all expenses for [vehicleId], newest-first.
     */
    fun observeForVehicle(vehicleId: Int): Flow<List<VehicleExpense>>

    /**
     * Reactive stream of all expenses for [vehicleId] whose date falls within
     * the calendar year bounded by [[fromMillis], [toMillis]] (epoch millis).
     */
    fun observeForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<VehicleExpense>>

    /**
     * Returns the total [VehicleExpense.amountCad] for [vehicleId] within the
     * given year window.  Returns 0.0 when no expenses exist for the period.
     */
    suspend fun getTotalForVehicleAndYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Double
}
