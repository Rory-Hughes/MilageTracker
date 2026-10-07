package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.PolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {

    // Returns the newly generated 'id' as a Long so we can assign it to the User
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertPolicy(policy: PolicyEntity): Long

    @Update
    suspend fun updatePolicy(policy: PolicyEntity)

    // Flow emits updates automatically if the policy changes
    @Query("SELECT * FROM policies WHERE id = :policyId LIMIT 1")
    fun getPolicyById(policyId: Int): Flow<PolicyEntity>

    // Useful for the Settings screen where a user might select from multiple custom rates
    @Query("SELECT * FROM policies")
    fun getAllPolicies(): Flow<List<PolicyEntity>>
}
