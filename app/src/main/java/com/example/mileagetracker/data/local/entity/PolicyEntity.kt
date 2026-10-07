package com.example.mileagetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val policyName: String, // e.g., "CRA Standard 2026" or "Acme Corp Flat Rate"
    val policyType: String, // e.g., "CRA_Standard" or "Custom_Company"

    val baseRate: Float, // e.g., 0.74f

    // These are nullable (?) because a custom company policy might just be a flat rate
    // with no secondary tier or threshold.
    val secondaryRate: Float? = null, // e.g., 0.67f
    val thresholdKm: Float? = null // e.g., 5000.0f
)
