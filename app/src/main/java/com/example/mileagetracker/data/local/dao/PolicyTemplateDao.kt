package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.PolicyTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [PolicyTemplateEntity] CRUD operations.
 */
@Dao
interface PolicyTemplateDao {

    /** Insert or replace a policy template. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(policy: PolicyTemplateEntity)

    /** Observe all policy templates ordered by name. */
    @Query("SELECT * FROM policy_templates ORDER BY name ASC")
    fun getAll(): Flow<List<PolicyTemplateEntity>>

    /** Return a single policy by its [id], or null if not found. */
    @Query("SELECT * FROM policy_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PolicyTemplateEntity?

    /** Delete a policy template by its [id]. */
    @Query("DELETE FROM policy_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
