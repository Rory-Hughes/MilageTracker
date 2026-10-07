package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.VehicleExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [VehicleExpenseEntity] (table: `vehicle_expenses`).
 *
 * ## Query notes
 * - [getForVehicle] returns a reactive [Flow] sorted newest-first so the
 *   expense list UI stays automatically up to date.
 * - [getTotalForVehicleAndYear] is a one-shot aggregate used by
 *   [com.example.mileagetracker.domain.usecase.expense.GetVehicleExpensesUseCase]
 *   to compute the annual deductible amount.
 * - [deleteById] is used by the edit/delete flow in
 *   [com.example.mileagetracker.ui.expense.VehicleExpenseViewModel].
 */
@Dao
interface VehicleExpenseDao {

    /**
     * Inserts a new expense.  [OnConflictStrategy.REPLACE] means that if a
     * row with the same [VehicleExpenseEntity.id] already exists (e.g. an
     * edit flow), it is atomically replaced.
     *
     * @return The row-id of the inserted (or replaced) row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: VehicleExpenseEntity): Long

    /**
     * Updates an existing expense row in place.
     *
     * @return The number of rows updated (0 if the row no longer exists).
     */
    @Update
    suspend fun update(expense: VehicleExpenseEntity): Int

    /**
     * Deletes the expense row with the given [id].
     *
     * @param id  The [VehicleExpenseEntity.id] of the row to delete.
     */
    @Query("DELETE FROM vehicle_expenses WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM vehicle_expenses WHERE id = :id")
    suspend fun getById(id: Int): VehicleExpenseEntity?

    /**
     * Reactive stream of all expenses for [vehicleId], ordered by
     * [VehicleExpenseEntity.date] descending (newest first).
     */
    @Query(
        """
        SELECT * FROM vehicle_expenses
        WHERE vehicle_id = :vehicleId
        ORDER BY date DESC
        """
    )
    fun getForVehicle(vehicleId: Int): Flow<List<VehicleExpenseEntity>>

    @Query(
        """
        SELECT * FROM vehicle_expenses
        WHERE vehicle_id = :vehicleId
        ORDER BY date DESC
        """
    )
    suspend fun getForVehicleOnce(vehicleId: Int): List<VehicleExpenseEntity>

    @Query("DELETE FROM vehicle_expenses WHERE vehicle_id = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: Int)

    /**
     * Reactive stream of all expenses for [vehicleId] whose [VehicleExpenseEntity.date]
     * falls within the given year window [[fromMillis], [toMillis]] (epoch millis).
     *
     * Used by [com.example.mileagetracker.domain.usecase.expense.GetVehicleExpensesUseCase]
     * to filter to the selected tax year.
     */
    @Query(
        """
        SELECT * FROM vehicle_expenses
        WHERE vehicle_id = :vehicleId
          AND date >= :fromMillis
          AND date <= :toMillis
        ORDER BY date DESC
        """
    )
    fun getForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<VehicleExpenseEntity>>

    /**
     * Returns the sum of [VehicleExpenseEntity.amountCad] for all expense rows
     * belonging to [vehicleId] within the given year window [[fromMillis], [toMillis]].
     *
     * Returns `0.0` (via SQLite COALESCE) when no rows exist for the period.
     *
     * Used by [com.example.mileagetracker.domain.usecase.expense.GetVehicleExpensesUseCase]
     * before multiplying by the vehicle's
     * business-use percentage to produce the deductible amount.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount_cad), 0.0) FROM vehicle_expenses
        WHERE vehicle_id = :vehicleId
          AND date >= :fromMillis
          AND date <= :toMillis
        """
    )
    suspend fun getTotalForVehicleAndYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): Double
}
