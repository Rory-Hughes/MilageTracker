package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    // For a mobile app, we usually just want the active logged-in profile
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    // A suspend function (instead of Flow) is useful when we need to do a one-time
    // check of the user's YTD distance right before saving a newly classified drive
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSync(): UserEntity?
}
