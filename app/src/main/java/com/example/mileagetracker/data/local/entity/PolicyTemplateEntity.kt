package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.mileagetracker.data.local.converter.StringListConverter
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.RateMode

/**
 * Room entity that persists a [PolicyTemplate] locally.
 *
 * [eligibleCategories] is stored as a comma-separated string via [StringListConverter].
 */
@Entity(tableName = "policy_templates")
@TypeConverters(StringListConverter::class)
data class PolicyTemplateEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo(name = "rate_mode")
    val rateMode: String, // RateMode.name()

    @ColumnInfo(name = "custom_rate_cents_per_km")
    val customRateCentsPerKm: Int?,

    @ColumnInfo(name = "eligible_categories")
    @TypeConverters(StringListConverter::class)
    val eligibleCategories: List<String>,

    @ColumnInfo(name = "exclude_commute")
    val excludeCommute: Boolean,

    @ColumnInfo(name = "require_purpose")
    val requirePurpose: Boolean,

    @ColumnInfo(name = "require_attachment")
    val requireAttachment: Boolean,

    @ColumnInfo(name = "max_daily_km")
    val maxDailyKm: Int?
) {
    fun toDomain(): PolicyTemplate = PolicyTemplate(
        id = id,
        name = name,
        rateMode = RateMode.valueOf(rateMode),
        customRateCentsPerKm = customRateCentsPerKm,
        eligibleCategories = eligibleCategories,
        excludeCommute = excludeCommute,
        requirePurpose = requirePurpose,
        requireAttachment = requireAttachment,
        maxDailyKm = maxDailyKm
    )
}

fun PolicyTemplate.toEntity() = PolicyTemplateEntity(
    id = id,
    name = name,
    rateMode = rateMode.name,
    customRateCentsPerKm = customRateCentsPerKm,
    eligibleCategories = eligibleCategories,
    excludeCommute = excludeCommute,
    requirePurpose = requirePurpose,
    requireAttachment = requireAttachment,
    maxDailyKm = maxDailyKm
)
