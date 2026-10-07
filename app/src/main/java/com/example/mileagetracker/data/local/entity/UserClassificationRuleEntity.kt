package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a user-defined classification rule.
 *
 * ## Table: `user_classification_rules`
 *
 * Each row represents one keyword-based rule that the user has created
 * via [RuleFormScreen].  Rows are loaded at classification time and wrapped
 * in [UserRuleClassificationRule] so they participate in the same
 * first-match-wins evaluation chain as the built-in rules.
 *
 * @param id          Auto-generated PK.
 * @param name        Short label shown in the rules list.
 * @param keyword     Case-insensitive substring matched against trip location /
 *                    purpose fields.
 * @param category    [TripCategory.name] stored as a string.
 * @param confidence  0.0–1.0 — must be ≥ 0.55 to result in auto-classification.
 * @param priority    Evaluation order; lower = higher priority. Default 50.
 * @param isEnabled   Whether this rule is active.
 * @param createdAt   Row-insert epoch millis.
 */
@Entity(
    tableName = "user_classification_rules",
    indices = [
        Index("owner_uid"),
        Index(value = ["owner_uid", "priority", "name"]),
        Index(value = ["owner_uid", "is_enabled", "priority"]),
    ],
)
data class UserClassificationRuleEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "owner_uid")
    val ownerUid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "keyword")
    val keyword: String,

    /** [TripCategory.name] — stored as string, resolved via runCatching on read. */
    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float = 0.75f,

    @ColumnInfo(name = "priority")
    val priority: Int = 50,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
