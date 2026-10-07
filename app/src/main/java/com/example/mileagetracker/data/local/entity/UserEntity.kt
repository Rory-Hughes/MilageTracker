package com.example.mileagetracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = PolicyEntity::class,
            parentColumns = ["id"],
            childColumns = ["activePolicyId"],
            // If a policy is deleted, don't delete the user. Just set this to null.
            onDelete = ForeignKey.Companion.SET_NULL
        )
    ],
    indices = [
        Index(value = ["activePolicyId"]) // Speeds up queries joining users and policies
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val email: String,

    val activePolicyId: Int? = null, // Links to the policies table

    // We store this here to quickly check if they've crossed the 5,000 km CRA threshold
    val totalYtdDistance: Float = 0.0f
)
