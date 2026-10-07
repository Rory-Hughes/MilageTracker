package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [UserClassificationRuleEntity] (table: `user_classification_rules`).
 */
@Dao
interface UserClassificationRuleDao {

    /** Insert or replace a rule. Returns the row-id of the upserted row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: UserClassificationRuleEntity): Long

    /** Update an existing rule (matched by primary key). */
    @Update
    suspend fun update(rule: UserClassificationRuleEntity)

    /** Hard-delete a rule by its primary key. */
    @Query("DELETE FROM user_classification_rules WHERE owner_uid = :ownerUid AND id = :id")
    suspend fun deleteById(ownerUid: String, id: Int)

    /** Reactive stream of all rules, ordered by priority ascending then name. */
    @Query(
        """
        SELECT * FROM user_classification_rules
        WHERE owner_uid = :ownerUid
        ORDER BY priority ASC, name ASC
        """
    )
    fun observeAll(ownerUid: String): Flow<List<UserClassificationRuleEntity>>

    @Query(
        """
        SELECT * FROM user_classification_rules
        WHERE owner_uid = :ownerUid
        ORDER BY priority ASC, name ASC
        """
    )
    suspend fun getAllOnce(ownerUid: String): List<UserClassificationRuleEntity>

    /** One-shot fetch of all enabled rules — used at classification time. */
    @Query(
        """
        SELECT * FROM user_classification_rules
        WHERE owner_uid = :ownerUid AND is_enabled = 1
        ORDER BY priority ASC
        """
    )
    suspend fun getEnabledRules(ownerUid: String): List<UserClassificationRuleEntity>
}
